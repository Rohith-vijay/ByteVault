const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname);
const backendDir = path.join(rootDir, 'backend');
const logsDir = path.join(rootDir, 'logs');

if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

// Load .env if present
const envPath = path.join(rootDir, '.env');
if (fs.existsSync(envPath)) {
  const envContent = fs.readFileSync(envPath, 'utf-8');
  for (const line of envContent.split('\n')) {
    const trimmed = line.trim();
    if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
      const idx = trimmed.indexOf('=');
      const key = trimmed.substring(0, idx).trim();
      const val = trimmed.substring(idx + 1).trim();
      if (!process.env[key]) {
        process.env[key] = val;
      }
    }
  }
}

const net = require('net');

function isPortInUse(port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ port, host: '127.0.0.1' }, () => {
      socket.end();
      resolve(true);
    });
    socket.on('error', () => {
      resolve(false);
    });
  });
}

// 8 Active Services + 1 Secondary Product Instance for ByteVault Architecture
const services = [
  { name: 'discovery-server', port: 8761, module: 'discovery-server', delayBeforeNext: 8000 },
  { name: 'identity-service', port: 8081, module: 'identity-service', delayBeforeNext: 3000 },
  { name: 'product-service', port: 8084, module: 'product-service', delayBeforeNext: 3000 },
  { name: 'product-service-2', port: 8085, module: 'product-service', delayBeforeNext: 3000 },
  { name: 'cart-service', port: 8086, module: 'cart-service', delayBeforeNext: 3000 },
  { name: 'order-service', port: 8087, module: 'order-service', delayBeforeNext: 3000 },
  { name: 'payment-service', port: 8091, module: 'payment-service', delayBeforeNext: 3000 },
  { name: 'fulfillment-service', port: 8088, module: 'fulfillment-service', delayBeforeNext: 3000 },
  { name: 'notification-service', port: 8090, module: 'notification-service', delayBeforeNext: 3000 },
  { name: 'api-gateway', port: 8080, module: 'api-gateway', delayBeforeNext: 4000 }
];

const processes = [];

function startService(svc) {
  const jarPath = path.join(backendDir, svc.module, 'target', `${svc.module}-1.0.0-SNAPSHOT.jar`);
  if (!fs.existsSync(jarPath)) {
    console.error(`[ERROR] JAR not found for ${svc.name} at ${jarPath}`);
    return;
  }

  const outLog = fs.openSync(path.join(logsDir, `${svc.name}.log`), 'a');
  const errLog = fs.openSync(path.join(logsDir, `${svc.name}-error.log`), 'a');

  console.log(`[STARTING] ${svc.name} on port :${svc.port}...`);
  const javaArgs = [
    `-Dserver.port=${svc.port}`,
    '-Xms64m',
    '-Xmx320m',
    '-XX:MetaspaceSize=64m',
    '-XX:MaxMetaspaceSize=180m',
    '-Xss384k',
    '-XX:+TieredCompilation',
    '-XX:TieredStopAtLevel=1'
  ];
  if (process.env.DB_PASSWORD) {
    javaArgs.push(`-Dspring.datasource.password=${process.env.DB_PASSWORD}`);
  }
  if (process.env.CART_DB_PASSWORD) {
    javaArgs.push(`-Dspring.datasource.password=${process.env.CART_DB_PASSWORD}`);
  }
  if (process.env.GATEWAY_SECRET) {
    javaArgs.push(`-Dapp.gateway.secret=${process.env.GATEWAY_SECRET}`);
  }
  if (process.env.JWT_SECRET) {
    javaArgs.push(`-Dapp.jwt.secret=${process.env.JWT_SECRET}`);
  }
  javaArgs.push(`-Deureka.instance.instanceId=${svc.name}:${svc.port}`);
  javaArgs.push('-jar', jarPath);

  const child = spawn('java', javaArgs, {
    cwd: backendDir,
    env: process.env,
    stdio: ['ignore', outLog, errLog],
    detached: false
  });

  child.on('error', (err) => {
    console.error(`[PROCESS ERROR] ${svc.name}:`, err);
  });

  child.on('exit', (code, signal) => {
    console.log(`[EXIT] ${svc.name} exited with code ${code} signal ${signal}`);
    if (!isShuttingDown) {
      console.log(`[WATCHDOG] Auto-restarting ${svc.name} in 4 seconds...`);
      setTimeout(() => startService(svc), 4000);
    }
  });

  processes.push({ name: svc.name, port: svc.port, pid: child.pid, proc: child });
}

let isShuttingDown = false;

async function run() {
  console.log('=== BYTEVAULT MEDIA ACTIVE 8-SERVICE SUPERVISOR STARTING ===');
  console.log('Persistence: PostgreSQL 100% (identity, product, cart, order, payment, fulfillment)');

  for (const svc of services) {
    const inUse = await isPortInUse(svc.port);
    if (inUse) {
      console.log(`[ALREADY RUNNING] ${svc.name} is already listening on :${svc.port}, skipping launch.`);
    } else {
      startService(svc);
      if (svc.delayBeforeNext) {
        await new Promise(r => setTimeout(r, svc.delayBeforeNext));
      }
    }
  }

  console.log('\n=== ALL 8 ACTIVE MICROSERVICES + GATEWAY LAUNCHED ===\nSupervising processes...\n');

  setInterval(() => {
    // Keep alive heartbeat
  }, 10000);
}

process.on('SIGINT', () => {
  console.log('Terminating all ByteVault processes...');
  isShuttingDown = true;
  for (const p of processes) {
    try { p.proc.kill(); } catch (e) {}
  }
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('Terminating all ByteVault processes...');
  isShuttingDown = true;
  for (const p of processes) {
    try { p.proc.kill(); } catch (e) {}
  }
  process.exit(0);
});

run().catch(err => {
  console.error('Fatal runner error:', err);
});
