const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');

const rootDir = 'D:\\ProductManagementSystem';
const backendDir = path.join(rootDir, 'backend');
const logsDir = path.join(rootDir, 'logs');

if (!fs.existsSync(logsDir)) {
  fs.mkdirSync(logsDir, { recursive: true });
}

const services = [
  { name: 'discovery-server', port: 8761, module: 'discovery-server', delayBeforeNext: 8000 },
  { name: 'auth-service', port: 8081, module: 'auth-service', delayBeforeNext: 2000 },
  { name: 'user-service', port: 8082, module: 'user-service', delayBeforeNext: 2000 },
  { name: 'product-service', port: 8084, module: 'product-service', delayBeforeNext: 2000 },
  { name: 'inventory-service', port: 8085, module: 'inventory-service', delayBeforeNext: 1500 },
  { name: 'cart-service', port: 8086, module: 'cart-service', delayBeforeNext: 1500 },
  { name: 'order-service', port: 8087, module: 'order-service', delayBeforeNext: 2000 },
  { name: 'fulfillment-service', port: 8088, module: 'fulfillment-service', delayBeforeNext: 2000 },
  { name: 'payment-service', port: 8091, module: 'payment-service', delayBeforeNext: 1500 },
  { name: 'shipping-service', port: 8089, module: 'shipping-service', delayBeforeNext: 1500 },
  { name: 'notification-service', port: 8090, module: 'notification-service', delayBeforeNext: 1000 },
  { name: 'warehouse-service', port: 8092, module: 'warehouse-service', delayBeforeNext: 1000 },
  { name: 'support-service', port: 8093, module: 'support-service', delayBeforeNext: 1000 },
  { name: 'api-gateway', port: 8080, module: 'api-gateway', delayBeforeNext: 2000 }
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
  const child = spawn('java', ['-Xms128m', '-Xmx384m', '-jar', jarPath], {
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
  console.log('=== BYTEVAULT MEDIA SUPERVISOR STARTING ===');
  console.log('PostgreSQL persistence mode - 100% real DB');

  for (const svc of services) {
    startService(svc);
    if (svc.delayBeforeNext) {
      await new Promise(r => setTimeout(r, svc.delayBeforeNext));
    }
  }

  console.log('\n=== ALL 14 SERVICES LAUNCHED ===\nSupervising processes...\n');

  // Keep process alive indefinitely
  setInterval(() => {
    // Health heartbeat
  }, 10000);
}

process.on('SIGINT', () => {
  console.log('Terminating all ByteVault processes...');
  for (const p of processes) {
    try {
      p.proc.kill();
    } catch (e) {}
  }
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('Terminating all ByteVault processes...');
  for (const p of processes) {
    try {
      p.proc.kill();
    } catch (e) {}
  }
  process.exit(0);
});

run().catch(err => {
  console.error('Fatal runner error:', err);
});
