const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const rootDir = 'D:\\ProductManagementSystem';
const backendDir = path.join(rootDir, 'backend');
const logsDir = path.join(rootDir, 'logs');

if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

// 8 Active Services for ByteVault Architecture
const services = [
  { name: 'discovery-server', port: 8761, module: 'discovery-server', delayBeforeNext: 12000 },
  { name: 'identity-service', port: 8081, module: 'identity-service', delayBeforeNext: 3000 },
  { name: 'product-service', port: 8084, module: 'product-service', delayBeforeNext: 3000 },
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
  const child = spawn('java', [
    '-Xms64m',
    '-Xmx320m',
    '-XX:MetaspaceSize=64m',
    '-XX:MaxMetaspaceSize=180m',
    '-Xss384k',
    '-XX:+TieredCompilation',
    '-XX:TieredStopAtLevel=1',
    '-jar',
    jarPath
  ], {
    cwd: backendDir,
    stdio: ['ignore', outLog, errLog],
    detached: false
  });

  child.on('error', (err) => {
    console.error(`[PROCESS ERROR] ${svc.name}:`, err);
  });

  child.on('exit', (code, signal) => {
    console.log(`[EXIT] ${svc.name} exited with code ${code} signal ${signal}`);
  });

  processes.push({ name: svc.name, port: svc.port, pid: child.pid, proc: child });
}

async function run() {
  console.log('=== BYTEVAULT MEDIA ACTIVE 8-SERVICE SUPERVISOR STARTING ===');
  console.log('Persistence: PostgreSQL 100% (identity, product, cart, order, payment, fulfillment)');

  for (const svc of services) {
    startService(svc);
    if (svc.delayBeforeNext) {
      await new Promise(r => setTimeout(r, svc.delayBeforeNext));
    }
  }

  console.log('\n=== ALL 8 ACTIVE MICROSERVICES + GATEWAY LAUNCHED ===\nSupervising processes...\n');

  setInterval(() => {
    // Keep alive heartbeat
  }, 10000);
}

process.on('SIGINT', () => {
  console.log('Terminating all ByteVault processes...');
  for (const p of processes) {
    try { p.proc.kill(); } catch (e) {}
  }
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('Terminating all ByteVault processes...');
  for (const p of processes) {
    try { p.proc.kill(); } catch (e) {}
  }
  process.exit(0);
});

run().catch(err => {
  console.error('Fatal runner error:', err);
});
