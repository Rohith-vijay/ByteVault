const { execSync } = require('child_process');

const ports = [
  { name: 'discovery-server', port: 8761 },
  { name: 'identity-service', port: 8081 },
  { name: 'product-service', port: 8084 },
  { name: 'cart-service', port: 8086 },
  { name: 'order-service', port: 8087 },
  { name: 'payment-service', port: 8091 },
  { name: 'fulfillment-service', port: 8088 },
  { name: 'notification-service', port: 8090 },
  { name: 'api-gateway', port: 8080 }
];

console.log('=== BYTEVAULT ACTIVE SERVICE RAM CONSUMPTION ===\n');

let totalWorkingSetMb = 0;
let totalPrivateMb = 0;

for (const svc of ports) {
  try {
    const net = execSync(`netstat -ano | findstr :${svc.port}`, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'ignore'] });
    const lines = net.trim().split('\n');
    let pid = null;
    for (const l of lines) {
      const parts = l.trim().split(/\s+/);
      if (parts.length >= 5 && parts[1].endsWith(`:${svc.port}`)) {
        pid = parts[parts.length - 1];
        break;
      }
    }
    if (pid && pid !== '0') {
      const psOut = execSync(`powershell -NoProfile -Command "Get-Process -Id ${pid} | Select-Object WorkingSet64, PrivateMemorySize64 | ConvertTo-Json"`, { encoding: 'utf-8' });
      const mem = JSON.parse(psOut);
      const wsMb = (mem.WorkingSet64 / (1024 * 1024)).toFixed(1);
      const pmMb = (mem.PrivateMemorySize64 / (1024 * 1024)).toFixed(1);
      totalWorkingSetMb += parseFloat(wsMb);
      totalPrivateMb += parseFloat(pmMb);
      console.log(`- ${svc.name.padEnd(22)} (Port :${svc.port}, PID ${pid}): WorkingSet = ${wsMb.padStart(6)} MB, Private = ${pmMb.padStart(6)} MB`);
    } else {
      console.log(`- ${svc.name.padEnd(22)} (Port :${svc.port}): NOT LISTENING`);
    }
  } catch (e) {
    console.log(`- ${svc.name.padEnd(22)} (Port :${svc.port}): Error querying`);
  }
}

console.log(`\nTOTAL ACTIVE STACK RAM (9 Processes):`);
console.log(`Total Working Set: ${totalWorkingSetMb.toFixed(1)} MB (~${(totalWorkingSetMb / 1024).toFixed(2)} GB)`);
console.log(`Total Private Memory: ${totalPrivateMb.toFixed(1)} MB (~${(totalPrivateMb / 1024).toFixed(2)} GB)`);
