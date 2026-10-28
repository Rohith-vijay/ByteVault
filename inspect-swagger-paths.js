const http = require('http');

const services = [
  'identity-service',
  'product-service',
  'order-service',
  'cart-service',
  'payment-service',
  'fulfillment-service',
  'notification-service'
];

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, error: e.message, raw: data.substring(0, 200) });
        }
      });
    }).on('error', reject);
  });
}

async function inspect() {
  console.log('--- SWAGGER & OPENAPI PATH INSPECTION ---');
  for (const s of services) {
    const url = `http://localhost:8080/v3/api-docs/${s}`;
    try {
      const res = await fetchJson(url);
      if (res.status === 200 && res.body && res.body.paths) {
        const pathCount = Object.keys(res.body.paths).length;
        console.log(`[PASS] ${s} -> ${pathCount} paths`);
        for (const [p, methods] of Object.entries(res.body.paths)) {
          console.log(`    ${Object.keys(methods).join(',').toUpperCase()} ${p}`);
        }
      } else {
        console.log(`[FAIL] ${s} -> Status ${res.status}`);
      }
    } catch (e) {
      console.log(`[ERROR] ${s}: ${e.message}`);
    }
  }
}

inspect();
