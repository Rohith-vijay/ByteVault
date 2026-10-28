const { spawn } = require('child_process');
const http = require('http');
const path = require('path');

function getEurekaApps() {
  return new Promise((resolve) => {
    const req = http.get('http://localhost:8761/eureka/apps', {
      headers: { 'Accept': 'application/json' },
      timeout: 2000
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          const apps = parsed.applications?.application || [];
          const upApps = [];
          for (const app of apps) {
            const instances = Array.isArray(app.instance) ? app.instance : [app.instance];
            const hasUp = instances.some(i => i && i.status === 'UP');
            if (hasUp) {
              upApps.push(app.name.toUpperCase());
            }
          }
          resolve(upApps);
        } catch (e) {
          resolve([]);
        }
      });
    });
    req.on('error', () => resolve([]));
    req.on('timeout', () => { req.destroy(); resolve([]); });
  });
}

async function runMeasurement(runNumber) {
  console.log(`\n========================================`);
  console.log(`Starting Cold Start Run #${runNumber}...`);
  console.log(`========================================`);

  const startTime = Date.now();
  const runner = spawn('node', ['start-active-stack.js'], {
    cwd: 'D:\\ProductManagementSystem',
    stdio: 'inherit'
  });

  const expectedApps = [
    'IDENTITY-SERVICE',
    'PRODUCT-SERVICE',
    'CART-SERVICE',
    'ORDER-SERVICE',
    'PAYMENT-SERVICE',
    'FULFILLMENT-SERVICE',
    'NOTIFICATION-SERVICE',
    'API-GATEWAY'
  ];

  const appDiscoveredAt = {};
  let allUp = false;
  const timeoutMs = 180000; // 3 minutes max

  while (Date.now() - startTime < timeoutMs) {
    await new Promise(r => setTimeout(r, 2000));
    const upApps = await getEurekaApps();
    const elapsedSec = ((Date.now() - startTime) / 1000).toFixed(1);

    for (const app of upApps) {
      if (!appDiscoveredAt[app]) {
        appDiscoveredAt[app] = elapsedSec;
        console.log(`[EUREKA] + ${app} reported UP at ${elapsedSec}s`);
      }
    }

    const missing = expectedApps.filter(a => !upApps.includes(a));
    if (missing.length === 0) {
      allUp = true;
      console.log(`\n>>> SUCCESS: All ${expectedApps.length} applications UP in Eureka at ${elapsedSec}s! <<<`);
      break;
    }
  }

  const totalTime = ((Date.now() - startTime) / 1000).toFixed(1);
  if (!allUp) {
    const missing = expectedApps.filter(a => !appDiscoveredAt[a]);
    console.error(`\n>>> TIMEOUT: Cold start #${runNumber} failed to register all apps within 180s. Missing: ${missing.join(', ')}`);
  }

  console.log(`Summary of Run #${runNumber}:`);
  for (const app of expectedApps) {
    console.log(`  - ${app}: ${appDiscoveredAt[app] ? appDiscoveredAt[app] + 's' : 'FAIL / NOT UP'}`);
  }
  console.log(`Total time for Run #${runNumber}: ${totalTime}s`);

  // Terminate runner
  runner.kill();
  return { runNumber, success: allUp, totalTime, appDiscoveredAt };
}

module.exports = { runMeasurement };

if (require.main === module) {
  (async () => {
    const result = await runMeasurement(1);
    console.log('Finished single cold start measurement.');
  })();
}
