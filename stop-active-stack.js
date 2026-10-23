const { execSync } = require('child_process');

const ports = [8761, 8080, 8081, 8084, 8086, 8087, 8088, 8090, 8091];

console.log('Stopping active ByteVault services on ports:', ports.join(', '));

for (const port of ports) {
  try {
    const output = execSync(`netstat -ano | findstr :${port}`, { encoding: 'utf-8', stdio: ['ignore', 'pipe', 'ignore'] });
    const lines = output.trim().split('\n');
    const pids = new Set();
    for (const line of lines) {
      const parts = line.trim().split(/\s+/);
      if (parts.length >= 5 && parts[1].endsWith(`:${port}`)) {
        const pid = parts[parts.length - 1];
        if (pid && pid !== '0') {
          pids.add(pid);
        }
      }
    }
    for (const pid of pids) {
      try {
        console.log(`Killing PID ${pid} on port ${port}...`);
        execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore' });
      } catch (e) {}
    }
  } catch (e) {
    // Port not in use
  }
}
console.log('All active ByteVault services stopped.');
