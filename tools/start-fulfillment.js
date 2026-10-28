const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const rootDir = path.resolve(__dirname, '..');
const backendDir = path.join(rootDir, 'backend');
const jarPath = path.join(backendDir, 'fulfillment-service', 'target', 'fulfillment-service-1.0.0-SNAPSHOT.jar');

// Load .env
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

const dbPass = process.env.DB_PASSWORD || '';

console.log('Starting fulfillment-service on port 8088...');
const args = [
  '-Xms64m',
  '-Xmx320m',
  '-XX:MetaspaceSize=64m',
  '-XX:MaxMetaspaceSize=180m',
  '-Xss384k',
  '-XX:+TieredCompilation',
  '-XX:TieredStopAtLevel=1'
];

if (dbPass) {
  args.push(`-Dspring.datasource.password=${dbPass}`);
}

args.push('-jar', jarPath);

const child = spawn('java', args, {
  cwd: backendDir,
  env: process.env,
  stdio: 'ignore',
  detached: true
});

child.unref();
console.log('Fulfillment service launched with PID:', child.pid);
process.exit(0);
