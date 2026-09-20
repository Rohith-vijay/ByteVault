const http = require('http');

function decodeJwt(token) {
  try {
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const payload = Buffer.from(parts[1], 'base64').toString('utf8');
    return JSON.parse(payload);
  } catch (e) {
    return null;
  }
}

async function request(url, options = {}, data = null) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const reqOptions = {
      hostname: u.hostname,
      port: u.port,
      path: u.pathname + u.search,
      method: options.method || 'GET',
      headers: options.headers || {}
    };

    if (data) {
      if (typeof data === 'object') {
        data = JSON.stringify(data);
        reqOptions.headers['Content-Type'] = 'application/json';
      }
      reqOptions.headers['Content-Length'] = Buffer.byteLength(data);
    }

    const req = http.request(reqOptions, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        let parsed = body;
        try {
          parsed = JSON.parse(body);
        } catch (e) {}
        resolve({ status: res.statusCode, headers: res.headers, body: parsed, rawBody: body });
      });
    });

    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function runTests() {
  console.log('===============================================================');
  console.log('  BYTEVAULT MEDIA — REVIEW 1 AUTOMATED VERIFICATION SUITE');
  console.log('===============================================================\n');

  const results = [];
  function record(id, title, pass, details) {
    console.log(`[${pass ? 'PASS' : 'FAIL'}] ${id}: ${title}`);
    if (details) console.log(`       Details: ${details}`);
    results.push({ id, title, pass, details });
  }

  // 1. Discovery Server Check
  try {
    const res = await request('http://localhost:8761/');
    record('TEST-01', 'Eureka Discovery Server Running (:8761)', res.status === 200, `HTTP ${res.status}`);
  } catch (e) {
    record('TEST-01', 'Eureka Discovery Server Running (:8761)', false, e.message);
  }

  // 2. API Gateway Check
  try {
    const res = await request('http://localhost:8080/actuator/health');
    record('TEST-02', 'API Gateway Responsive (:8080)', res.status === 200 || res.status === 404, `Status ${res.status}`);
  } catch (e) {
    record('TEST-02', 'API Gateway Responsive (:8080)', false, e.message);
  }

  // 3. Frontend Portal Check
  try {
    const res = await request('http://localhost:5173/');
    const containsTitle = res.rawBody.includes('ByteVault Media');
    record('TEST-03', 'ByteVault Frontend Live (:5173)', res.status === 200 && containsTitle, `Title verified in HTML`);
  } catch (e) {
    record('TEST-03', 'ByteVault Frontend Live (:5173)', false, e.message);
  }

  // 4. Admin Seed Login
  let adminToken = null;
  let adminUserId = null;
  try {
    const adminRes = await request('http://localhost:8080/api/v1/auth/login', {
      method: 'POST'
    }, {
      email: 'admin@bytevault.com',
      password: 'AdminPass123!'
    });

    const token = adminRes.body?.data?.token || adminRes.body?.token;
    if (token) {
      adminToken = token;
      const claims = decodeJwt(adminToken);
      adminUserId = claims?.id || claims?.sub;
    }
    record('TEST-04', 'Admin Seed Login in PostgreSQL (admin@bytevault.com)', !!adminToken, `Admin ID: ${adminUserId}`);
  } catch (e) {
    record('TEST-04', 'Admin Seed Login', false, e.message);
  }

  // 5. Customer Registration (via API Gateway :8080 into PostgreSQL)
  const timestamp = Date.now();
  const testEmail = `reviewer_${timestamp}@bytevault.com`;
  const testPassword = 'Password123!';
  let customerToken = null;
  let customerUserId = null;

  try {
    const regRes = await request('http://localhost:8080/api/v1/auth/register', {
      method: 'POST'
    }, {
      email: testEmail,
      password: testPassword,
      fullName: 'Review Tester',
      role: 'CUSTOMER'
    });

    const token = regRes.body?.data?.token || regRes.body?.token;
    if (token) {
      customerToken = token;
      const claims = decodeJwt(customerToken);
      customerUserId = claims?.id || claims?.sub;
    }
    record('TEST-05', 'Customer Registration in PostgreSQL (auth_db/user_db)', !!customerToken, `Customer User ID: ${customerUserId}`);
  } catch (e) {
    record('TEST-05', 'Customer Registration in PostgreSQL', false, e.message);
  }

  // 6. Customer Login (via API Gateway :8080)
  try {
    const loginRes = await request('http://localhost:8080/api/v1/auth/login', {
      method: 'POST'
    }, {
      email: testEmail,
      password: testPassword
    });

    const token = loginRes.body?.data?.token || loginRes.body?.token;
    if (token) {
      customerToken = token;
      const claims = decodeJwt(customerToken);
      customerUserId = claims?.id || claims?.sub || customerUserId;
    }
    record('TEST-06', 'Customer Login & JWT Generation', !!customerToken, `Status ${loginRes.status}`);
  } catch (e) {
    record('TEST-06', 'Customer Login & JWT Generation', false, e.message);
  }

  // 7. Vendor Registration, Profile Auto-Init & Admin Approval Workflow
  const vendorEmail = `vendor_${timestamp}@bytevault.com`;
  let vendorToken = null;
  let vendorId = null;
  try {
    const vReg = await request('http://localhost:8080/api/v1/auth/register', {
      method: 'POST'
    }, {
      email: vendorEmail,
      password: testPassword,
      fullName: 'Quantum Architecture Studio',
      role: 'VENDOR'
    });

    vendorToken = vReg.body?.data?.token || vReg.body?.token;
    if (vendorToken) {
      const claims = decodeJwt(vendorToken);
      vendorId = claims?.id || claims?.sub;
    }

    // Initialize vendor profile
    await request('http://localhost:8080/api/v1/users/me/vendor-profile', {
      headers: {
        'Authorization': `Bearer ${vendorToken}`
      }
    });

    // Approve vendor via Admin API
    let approveRes = null;
    if (vendorId && adminToken) {
      approveRes = await request(`http://localhost:8080/api/v1/users/admin/vendors/${vendorId}/approve`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${adminToken}`
        }
      });
    }

    const pass = !!vendorToken && approveRes?.status === 200;
    record('TEST-07', 'Vendor Registration & Admin Approval Lifecycle', pass, `Vendor ID: ${vendorId}, Approved: ${approveRes?.status === 200}`);
  } catch (e) {
    record('TEST-07', 'Vendor Registration & Approval', false, e.message);
  }

  // 8. Product Catalog Fetch (via API Gateway :8080)
  let products = [];
  try {
    const catRes = await request('http://localhost:8080/api/v1/products');
    const rawList = catRes.body?.data || catRes.body;
    products = Array.isArray(rawList) ? rawList : (rawList?.content || []);
    record('TEST-08', 'Product Catalog Query (product_db PostgreSQL)', catRes.status === 200, `Found ${products.length} products`);
  } catch (e) {
    record('TEST-08', 'Product Catalog Query', false, e.message);
  }

  // 9. Vendor Digital Product Creation & Publishing
  let publishedProductId = null;
  try {
    const prodRes = await request('http://localhost:8080/api/v1/products/vendor?publish=true', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${vendorToken}`
      }
    }, {
      name: `Spring Cloud Microservices Blueprint v${timestamp}`,
      description: 'Production-ready Spring Boot microservices architectural blueprint with PostgreSQL.',
      price: 49.99,
      productType: 'DIGITAL',
      tags: 'spring-boot, architecture, postgresql',
      fileName: 'blueprint-v1.zip',
      fileType: 'application/zip',
      fileSize: 10485760
    });

    const body = prodRes.body?.data || prodRes.body;
    publishedProductId = body?.id || body?.productId;
    const pass = (prodRes.status === 200 || prodRes.status === 201) && !!publishedProductId;
    record('TEST-09', 'Vendor Digital Product Creation & Publishing in PostgreSQL', pass, `Product ID: ${publishedProductId}`);
  } catch (e) {
    record('TEST-09', 'Vendor Digital Product Creation', false, e.message);
  }

  // 10. Digital Order Placement
  const orderTargetProductId = publishedProductId || (products.length > 0 ? (products[0].id || products[0].productId) : '11111111-1111-1111-1111-111111111111');
  let orderId = null;
  const orderPrice = 49.99;

  try {
    const orderRes = await request('http://localhost:8080/api/v1/orders', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${customerToken}`
      }
    }, {
      customerId: customerUserId,
      items: [
        {
          productId: orderTargetProductId,
          productTitle: 'Spring Cloud Microservices Blueprint',
          quantity: 1,
          unitPrice: orderPrice,
          productType: 'DIGITAL'
        }
      ],
      totalAmount: orderPrice,
      shippingAddress: 'Digital Delivery - Instant Cloud Access'
    });

    const body = orderRes.body?.data || orderRes.body;
    orderId = body?.id || body?.orderId;
    const pass = (orderRes.status === 200 || orderRes.status === 201) && !!orderId;
    record('TEST-10', 'Digital Order Creation in PostgreSQL (order_db)', pass, `Order ID: ${orderId}`);
  } catch (e) {
    record('TEST-10', 'Digital Order Creation', false, e.message);
  }

  // 11. Payment Processing & Synchronous Entitlement Trigger
  try {
    // 11a. Create Payment Order
    const initPayRes = await request('http://localhost:8080/api/v1/payments/create-order', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${customerToken}`
      }
    }, {
      amount: orderPrice,
      currency: 'INR',
      receipt: orderId
    });

    const initBody = initPayRes.body?.data || initPayRes.body;
    const razorpayOrderId = initBody?.orderId || `order_mock_${timestamp}`;

    // 11b. Verify Payment (synchronously executes digital fulfillment)
    const payRes = await request('http://localhost:8080/api/v1/payments/verify', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${customerToken}`
      }
    }, {
      razorpayOrderId: razorpayOrderId,
      razorpayPaymentId: `pay_mock_${timestamp}`,
      razorpaySignature: `sig_mock_${timestamp}`,
      dbOrderId: orderId,
      userId: customerUserId,
      amount: orderPrice
    });

    const payBody = payRes.body?.data || payRes.body;
    const pass = payRes.status === 200 && payBody?.status === 'SUCCESS' && payBody?.syncStatus === 'SYNCED';
    record('TEST-11', 'Payment Verification & Synchronous Fulfillment Trigger (payment_db/order_db)', pass, `Status: ${payBody?.status}, Sync: ${payBody?.syncStatus}`);
  } catch (e) {
    record('TEST-11', 'Payment Processing', false, e.message);
  }

  // 12. Fulfillment Entitlement Verification
  try {
    const entRes = await request('http://localhost:8080/api/v1/fulfillments/my-entitlements', {
      headers: {
        'Authorization': `Bearer ${customerToken}`
      }
    });

    const entitlements = entRes.body?.data || (Array.isArray(entRes.body) ? entRes.body : []);
    const pass = entRes.status === 200 && Array.isArray(entitlements) && entitlements.length > 0;
    record('TEST-12', 'Digital Entitlements Synchronously Created in PostgreSQL (fulfillment_db)', pass, `Entitlements count: ${entitlements.length}`);
  } catch (e) {
    record('TEST-12', 'Digital Entitlement Check', false, e.message);
  }

  // 13. Presigned Secure Download Generation
  try {
    const dlRes = await request(`http://localhost:8080/api/v1/downloads/${orderTargetProductId}`, {
      headers: {
        'Authorization': `Bearer ${customerToken}`
      }
    });

    const dlBody = dlRes.body?.data || dlRes.body;
    const downloadUrl = dlBody?.downloadUrl;
    const pass = dlRes.status === 200 && !!downloadUrl;
    record('TEST-13', 'Real Presigned Download URL Generation', pass, `Download URL: ${downloadUrl}`);
  } catch (e) {
    record('TEST-13', 'Real Presigned Download URL Generation', false, e.message);
  }

  // 14. RBAC Authorization: Customer forbidden from Admin endpoints
  try {
    const rbacRes = await request('http://localhost:8080/api/v1/users/admin/vendors/pending', {
      headers: {
        'Authorization': `Bearer ${customerToken}`
      }
    });

    const pass = rbacRes.status === 403 || rbacRes.status === 401;
    record('TEST-14', 'RBAC Enforcement (Customer restricted from Admin APIs)', pass, `HTTP ${rbacRes.status} (Forbidden/Unauthorized as expected)`);
  } catch (e) {
    record('TEST-14', 'RBAC Enforcement', false, e.message);
  }

  console.log('\n===============================================================');
  const total = results.length;
  const passed = results.filter(r => r.pass).length;
  console.log(`  VERIFICATION RESULTS: ${passed}/${total} PASSED`);
  console.log('===============================================================\n');
}

runTests().catch(console.error);
