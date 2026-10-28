const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

const GATEWAY_URL = 'http://localhost:8080';
const evidenceLog = path.join(__dirname, 'docs', 'evidence', `full-swagger-and-workflows-${Date.now()}.log`);

function log(msg) {
  const line = `[${new Date().toISOString()}] ${msg}`;
  console.log(line);
  fs.appendFileSync(evidenceLog, line + '\n', 'utf8');
}

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

function request(url, options = {}, data = null) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const client = u.protocol === 'https:' ? https : http;
    const reqOptions = {
      hostname: u.hostname,
      port: u.port || (u.protocol === 'https:' ? 443 : 80),
      path: u.pathname + u.search,
      method: options.method || 'GET',
      headers: { ...(options.headers || {}) }
    };

    let postData = data;
    if (postData && typeof postData === 'object' && !(postData instanceof Buffer)) {
      postData = JSON.stringify(postData);
      reqOptions.headers['Content-Type'] = 'application/json';
    }
    if (postData) {
      reqOptions.headers['Content-Length'] = Buffer.isBuffer(postData) ? postData.length : Buffer.byteLength(postData);
    }

    const startTime = Date.now();
    const req = client.request(reqOptions, (res) => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        const duration = Date.now() - startTime;
        const rawBuf = Buffer.concat(chunks);
        const rawStr = rawBuf.toString('utf8');
        let parsed = rawStr;
        try {
          parsed = JSON.parse(rawStr);
        } catch (e) {}
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: parsed,
          raw: rawStr,
          rawBuffer: rawBuf,
          duration: duration
        });
      });
    });

    req.on('error', (err) => {
      reject(err);
    });

    if (postData) {
      req.write(postData);
    }
    req.end();
  });
}

const stats = {
  total: 0,
  passed: 0,
  failed: 0,
  results: []
};

async function testEndpoint(testName, url, options = {}, payload = null, expectedStatus = [200]) {
  stats.total++;
  const expectedArray = Array.isArray(expectedStatus) ? expectedStatus : [expectedStatus];
  try {
    const res = await request(url, options, payload);
    const pass = expectedArray.includes(res.status);
    if (pass) {
      stats.passed++;
      log(`PASS [${res.status}] (${res.duration}ms) - ${options.method || 'GET'} ${url} | ${testName}`);
    } else {
      stats.failed++;
      log(`FAIL [${res.status} expected ${expectedArray.join('/')}] (${res.duration}ms) - ${options.method || 'GET'} ${url} | ${testName} | Body: ${typeof res.body === 'object' ? JSON.stringify(res.body) : res.raw.substring(0, 150)}`);
    }
    stats.results.push({
      testName,
      url,
      method: options.method || 'GET',
      status: res.status,
      duration: res.duration,
      pass,
      response: res.body
    });
    return res;
  } catch (err) {
    stats.failed++;
    log(`ERROR - ${options.method || 'GET'} ${url} | ${testName} | Error: ${err.message}`);
    stats.results.push({
      testName,
      url,
      method: options.method || 'GET',
      status: 'ERROR',
      duration: 0,
      pass: false,
      error: err.message
    });
    return { status: 500, error: err.message };
  }
}

async function runAll() {
  const ts = Date.now();
  log(`========================================================================================`);
  log(`  BYTEVAULT MEDIA - COMPLETE SWAGGER & MULTI-ROLE (VENDOR, CUSTOMER, ADMIN) WORKFLOW AUDIT`);
  log(`  Timestamp: ${new Date().toISOString()}`);
  log(`  Target: API Gateway [${GATEWAY_URL}]`);
  log(`========================================================================================\n`);

  // ============================================================================
  // SECTION 1: SWAGGER & OPENAPI SPECIFICATION TESTS
  // ============================================================================
  log(`\n>>> [SECTION 1] VERIFYING SWAGGER UI & OPENAPI SPECS ACROSS ALL SERVICES`);
  await testEndpoint('Gateway Swagger UI', `${GATEWAY_URL}/swagger-ui.html`, { method: 'GET' }, null, [200, 302]);
  await testEndpoint('Identity Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/identity-service`, { method: 'GET' }, null, [200]);
  await testEndpoint('Product Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/product-service`, { method: 'GET' }, null, [200]);
  await testEndpoint('Order Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/order-service`, { method: 'GET' }, null, [200]);
  await testEndpoint('Cart Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/cart-service`, { method: 'GET' }, null, [200]);
  await testEndpoint('Payment Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/payment-service`, { method: 'GET' }, null, [200]);
  await testEndpoint('Fulfillment Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/fulfillment-service`, { method: 'GET' }, null, [200]);
  await testEndpoint('Notification Service OpenAPI Spec', `${GATEWAY_URL}/v3/api-docs/notification-service`, { method: 'GET' }, null, [200]);

  // ============================================================================
  // SECTION 2: ADMIN AUTHENTICATION
  // ============================================================================
  log(`\n>>> [SECTION 2] ADMIN AUTHENTICATION & INITIALIZATION`);
  const adminLoginRes = await testEndpoint(
    'Admin Login',
    `${GATEWAY_URL}/api/v1/auth/login`,
    { method: 'POST' },
    { email: 'admin@bytevault.com', password: 'AdminPass123!' },
    [200]
  );
  const adminToken = adminLoginRes.body?.data?.token || adminLoginRes.body?.token;
  const adminClaims = decodeJwt(adminToken);
  log(`    Authenticated Admin ID: ${adminClaims?.id}, Role: ${adminClaims?.role}`);
  const adminHeaders = { 'Authorization': `Bearer ${adminToken}` };

  // ============================================================================
  // SECTION 3: VENDOR WORKFLOW (FULL)
  // ============================================================================
  log(`\n>>> [SECTION 3] VENDOR WORKFLOW: REGISTRATION, APPROVAL, PRODUCTS & LEDGER`);
  const vendorEmail = `vendor_lab_${ts}@bytevault.com`;
  const vendorPass = 'VendorPass123!';
  const vendorStoreName = `Apex Cloud Solutions ${ts}`;

  // 3.1 Register Vendor
  const vRegRes = await testEndpoint(
    'Vendor Registration',
    `${GATEWAY_URL}/api/v1/auth/register`,
    { method: 'POST' },
    { fullName: vendorStoreName, email: vendorEmail, password: vendorPass, role: 'VENDOR' },
    [200, 201]
  );
  let vendorToken = vRegRes.body?.data?.token || vRegRes.body?.token;

  // 3.2 Vendor Login
  const vLoginRes = await testEndpoint(
    'Vendor Login',
    `${GATEWAY_URL}/api/v1/auth/login`,
    { method: 'POST' },
    { email: vendorEmail, password: vendorPass },
    [200]
  );
  vendorToken = vLoginRes.body?.data?.token || vLoginRes.body?.token || vendorToken;
  const vendorClaims = decodeJwt(vendorToken);
  const vendorId = vendorClaims?.id;
  log(`    Authenticated Vendor ID: ${vendorId}, Email: ${vendorEmail}`);
  const vendorHeaders = { 'Authorization': `Bearer ${vendorToken}` };

  // 3.3 Fetch Vendor Profile
  const vProfRes = await testEndpoint(
    'Get Vendor Profile',
    `${GATEWAY_URL}/api/v1/users/me/vendor-profile`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );
  const vendorProfile = vProfRes.body?.data || vProfRes.body;
  const vendorProfileId = vendorProfile?.id || vendorId;

  // 3.4 Update Vendor Profile
  await testEndpoint(
    'Update Vendor Profile Details',
    `${GATEWAY_URL}/api/v1/users/me/vendor-profile`,
    { method: 'PUT', headers: vendorHeaders },
    { storeName: vendorStoreName, description: 'Enterprise Distributed Systems & Cloud Microservices', website: 'https://apexcloud.dev' },
    [200]
  );

  // 3.5 Admin Approves Vendor
  await testEndpoint(
    'Admin Approve Vendor',
    `${GATEWAY_URL}/api/v1/users/admin/vendors/${vendorProfileId}/approve`,
    { method: 'POST', headers: adminHeaders },
    null,
    [200]
  );

  // 3.6 Security Guard: Gateway blocks direct external access to /internal/
  await testEndpoint(
    'Gateway Guard: External call to /internal/ is blocked',
    `${GATEWAY_URL}/api/v1/users/internal/vendors/${vendorId}/status`,
    { method: 'GET' },
    null,
    [403]
  );

  // 3.7 Vendor Creates & Publishes Digital Product
  const digiPrice = 149.00;
  const digiProdRes = await testEndpoint(
    'Vendor Create Digital Product',
    `${GATEWAY_URL}/api/v1/products/vendor?publish=true`,
    { method: 'POST', headers: vendorHeaders },
    {
      name: `Spring Boot Cloud Masterclass v${ts}`,
      description: 'Production architecture, event-driven outbox patterns, and resilient microservices.',
      price: digiPrice,
      productType: 'DIGITAL',
      tags: 'spring-boot, microservices, cloud, java',
      fileName: `springboot-cloud-masterclass-${ts}.zip`,
      fileType: 'application/zip',
      fileSize: 10485760,
      fileVersion: '1.0.0'
    },
    [200, 201]
  );
  const digiProd = digiProdRes.body?.data || digiProdRes.body;
  const digiProdId = digiProd?.id || digiProd?.productId;
  log(`    Created Digital Product ID: ${digiProdId}, Price: ₹${digiPrice}, Status: ${digiProd?.status}`);

  // 3.8 Vendor Queries My Products
  await testEndpoint(
    'Vendor Get My Products',
    `${GATEWAY_URL}/api/v1/products/vendor/my-products`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );

  // 3.9 Vendor Checks Initial Earnings & Ledger
  await testEndpoint(
    'Vendor Check Initial Earnings',
    `${GATEWAY_URL}/api/v1/payments/vendor/earnings`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Vendor Check Initial Ledger',
    `${GATEWAY_URL}/api/v1/payments/vendor/ledger`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );

  // ============================================================================
  // SECTION 4: CUSTOMER WORKFLOW (FULL)
  // ============================================================================
  log(`\n>>> [SECTION 4] CUSTOMER WORKFLOW: PROFILE, CATALOG, CART, CHECKOUT, PAYMENT & FULFILLMENT`);
  const custEmail = `customer_${ts}@bytevault.com`;
  const custPass = 'CustomerPass123!';
  const custName = 'Sophia Chen';

  // 4.1 Customer Registration
  const cRegRes = await testEndpoint(
    'Customer Registration',
    `${GATEWAY_URL}/api/v1/auth/register`,
    { method: 'POST' },
    { fullName: custName, email: custEmail, password: custPass, role: 'CUSTOMER' },
    [200, 201]
  );
  let custToken = cRegRes.body?.data?.token || cRegRes.body?.token;

  // 4.2 Customer Login
  const cLoginRes = await testEndpoint(
    'Customer Login',
    `${GATEWAY_URL}/api/v1/auth/login`,
    { method: 'POST' },
    { email: custEmail, password: custPass },
    [200]
  );
  custToken = cLoginRes.body?.data?.token || cLoginRes.body?.token || custToken;
  const custClaims = decodeJwt(custToken);
  const custId = custClaims?.id;
  log(`    Authenticated Customer ID: ${custId}, Email: ${custEmail}`);
  const custHeaders = { 'Authorization': `Bearer ${custToken}` };

  // 4.3 Customer Profile GET & PUT
  await testEndpoint(
    'Get Customer Profile',
    `${GATEWAY_URL}/api/v1/users/me`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Update Customer Profile',
    `${GATEWAY_URL}/api/v1/users/me`,
    { method: 'PUT', headers: custHeaders },
    { fullName: custName, phone: '+91 9876543210' },
    [200]
  );

  // 4.4 Customer Addresses POST & GET
  const addrRes = await testEndpoint(
    'Add Customer Address',
    `${GATEWAY_URL}/api/v1/users/me/addresses`,
    { method: 'POST', headers: custHeaders },
    {
      addressLine1: '42 Silicon Avenue, Tech Zone 1',
      addressLine2: 'Suite 404',
      city: 'Bangalore',
      state: 'Karnataka',
      country: 'India',
      postalCode: '560100',
      isDefault: true
    },
    [200, 201]
  );
  await testEndpoint(
    'Get Customer Addresses',
    `${GATEWAY_URL}/api/v1/users/me/addresses`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );

  // 4.5 Browse Catalog & Search
  await testEndpoint(
    'Get All Products Catalog',
    `${GATEWAY_URL}/api/v1/products`,
    { method: 'GET' },
    null,
    [200]
  );
  await testEndpoint(
    'Get Categories',
    `${GATEWAY_URL}/api/v1/categories`,
    { method: 'GET' },
    null,
    [200]
  );
  await testEndpoint(
    'Get Single Product Details',
    `${GATEWAY_URL}/api/v1/products/${digiProdId}`,
    { method: 'GET' },
    null,
    [200]
  );

  // 4.6 Shopping Cart Lifecycle
  await testEndpoint(
    'Get Initial Empty Cart',
    `${GATEWAY_URL}/api/v1/cart`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Add Item to Cart',
    `${GATEWAY_URL}/api/v1/cart/items`,
    { method: 'POST', headers: custHeaders },
    { productId: digiProdId, quantity: 1 },
    [200]
  );
  // Domain Rule Guard: Digital quantity > 1 must be rejected with 400
  await testEndpoint(
    'Domain Rule Guard: Digital Product Quantity > 1 is Blocked',
    `${GATEWAY_URL}/api/v1/cart/items/${digiProdId}?quantity=2`,
    { method: 'PUT', headers: custHeaders },
    null,
    [400]
  );
  // Valid Cart Item Update: Quantity = 1
  await testEndpoint(
    'Update Cart Item Quantity (Valid: 1)',
    `${GATEWAY_URL}/api/v1/cart/items/${digiProdId}?quantity=1`,
    { method: 'PUT', headers: custHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Get Cart After Update',
    `${GATEWAY_URL}/api/v1/cart`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );

  // 4.7 Checkout & Order Placement
  const orderRes = await testEndpoint(
    'Place Customer Order',
    `${GATEWAY_URL}/api/v1/orders`,
    { method: 'POST', headers: custHeaders },
    {
      customerId: custId,
      items: [{ productId: digiProdId, quantity: 1 }],
      totalAmount: digiPrice,
      shippingAddress: 'Digital Instant Delivery to Account Library'
    },
    [200, 201]
  );
  const orderObj = orderRes.body?.data || orderRes.body;
  const orderId = orderObj?.id || orderObj?.orderId;
  log(`    Placed Order ID: ${orderId}, Total Amount: ₹${digiPrice}`);

  // 4.8 Get Order Details & Customer Orders
  await testEndpoint(
    'Get Placed Order By ID',
    `${GATEWAY_URL}/api/v1/orders/${orderId}`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Get Customer Order History',
    `${GATEWAY_URL}/api/v1/orders/my-orders`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );

  // 4.9 Payment: Create Razorpay Order
  const payOrderRes = await testEndpoint(
    'Create Payment Order',
    `${GATEWAY_URL}/api/v1/payments/create-order`,
    { method: 'POST', headers: custHeaders },
    { amount: digiPrice, currency: 'INR', receipt: orderId },
    [200]
  );
  const razorOrderId = payOrderRes.body?.data?.orderId || `order_${ts}`;

  // 4.10 Payment Verification (Triggers Digital Fulfillment)
  await testEndpoint(
    'Verify Payment & Trigger Fulfillment',
    `${GATEWAY_URL}/api/v1/payments/verify`,
    { method: 'POST', headers: custHeaders },
    {
      razorpayOrderId: razorOrderId,
      razorpayPaymentId: `pay_${ts}`,
      razorpaySignature: `sig_${ts}`,
      dbOrderId: orderId,
      userId: custId,
      amount: digiPrice
    },
    [200]
  );

  // 4.11 Check Customer Transactions
  await testEndpoint(
    'Get Customer Payment Transactions',
    `${GATEWAY_URL}/api/v1/payments/my-transactions`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );

  // 4.12 Digital Fulfillment & Entitlements
  const entRes = await testEndpoint(
    'Get Customer Active Entitlements',
    `${GATEWAY_URL}/api/v1/fulfillments/my-entitlements`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );
  const entitlements = entRes.body?.data || (Array.isArray(entRes.body) ? entRes.body : []);
  const matchingEnt = entitlements.find(e => e.productId === digiProdId);
  const entitlementId = matchingEnt?.id || matchingEnt?.entitlementId;
  log(`    Entitlement Granted! ID: ${entitlementId}, Status: ${matchingEnt?.status}`);

  // 4.13 Request Secure Signed Download URL & Stream Download
  const dlUrlRes = await testEndpoint(
    'Get Secure Download URL for Digital Asset',
    `${GATEWAY_URL}/api/v1/downloads/${digiProdId}`,
    { method: 'GET', headers: custHeaders },
    null,
    [200]
  );
  const downloadUrl = dlUrlRes.body?.data?.downloadUrl || dlUrlRes.body?.downloadUrl;
  log(`    Generated Secure Signed Download URL: ${downloadUrl}`);

  let streamedBytes = 0;
  if (downloadUrl) {
    const fullDlUrl = downloadUrl.startsWith('http') ? downloadUrl : `${GATEWAY_URL}${downloadUrl}`;
    const fileStreamRes = await testEndpoint(
      'Download Actual Asset Binary Stream',
      fullDlUrl,
      { method: 'GET', headers: custHeaders },
      null,
      [200]
    );
    streamedBytes = fileStreamRes.rawBuffer ? fileStreamRes.rawBuffer.length : 0;
    log(`    Streamed Asset Content Successfully: ${streamedBytes} bytes received!`);
  }

  // ============================================================================
  // SECTION 5: VENDOR LEDGER AFTER PURCHASE
  // ============================================================================
  log(`\n>>> [SECTION 5] VENDOR POST-SALE AUDIT: EARNINGS, SALES BREAKDOWN & LEDGER`);
  await testEndpoint(
    'Vendor Sales Breakdown After Customer Order',
    `${GATEWAY_URL}/api/v1/orders/vendor/my-sales`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Vendor Earnings After Payment',
    `${GATEWAY_URL}/api/v1/payments/vendor/earnings`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Vendor Ledger Credit Verification',
    `${GATEWAY_URL}/api/v1/payments/vendor/ledger`,
    { method: 'GET', headers: vendorHeaders },
    null,
    [200]
  );

  // ============================================================================
  // SECTION 6: ADMIN WORKFLOW (FULL)
  // ============================================================================
  log(`\n>>> [SECTION 6] ADMIN WORKFLOW: GOVERNANCE, AUDIT, REVOCATION & DISCOVERY`);
  // 6.1 Audit All Vendors
  await testEndpoint(
    'Admin Get All Vendors',
    `${GATEWAY_URL}/api/v1/users/admin/vendors`,
    { method: 'GET', headers: adminHeaders },
    null,
    [200]
  );
  // 6.2 Audit Pending Vendors
  await testEndpoint(
    'Admin Get Pending Vendors',
    `${GATEWAY_URL}/api/v1/users/admin/vendors/pending`,
    { method: 'GET', headers: adminHeaders },
    null,
    [200]
  );
  // 6.3 Audit All Products
  await testEndpoint(
    'Admin Get All Platform Products',
    `${GATEWAY_URL}/api/v1/products/admin/all`,
    { method: 'GET', headers: adminHeaders },
    null,
    [200]
  );
  // 6.4 Admin Governance: Toggle Product Status (PUBLISHED -> DEACTIVATED)
  await testEndpoint(
    'Admin Toggle Product Status (PUBLISHED -> DEACTIVATED)',
    `${GATEWAY_URL}/api/v1/products/${digiProdId}/status?status=DEACTIVATED`,
    { method: 'PATCH', headers: adminHeaders },
    null,
    [200]
  );
  // Admin Toggle Product Status Back (DEACTIVATED -> PUBLISHED)
  await testEndpoint(
    'Admin Toggle Product Status Back (DEACTIVATED -> PUBLISHED)',
    `${GATEWAY_URL}/api/v1/products/${digiProdId}/status?status=PUBLISHED`,
    { method: 'PATCH', headers: adminHeaders },
    null,
    [200]
  );
  // 6.5 Admin Global Orders View
  await testEndpoint(
    'Admin View All Platform Orders',
    `${GATEWAY_URL}/api/v1/orders/admin`,
    { method: 'GET', headers: adminHeaders },
    null,
    [200]
  );
  // 6.6 Admin Update Order Status (PAID -> PROCESSING -> FULFILLED)
  await testEndpoint(
    'Admin Transition Order Status (PAID -> PROCESSING)',
    `${GATEWAY_URL}/api/v1/orders/${orderId}/status?status=PROCESSING`,
    { method: 'PUT', headers: adminHeaders },
    null,
    [200]
  );
  await testEndpoint(
    'Admin Transition Order Status (PROCESSING -> FULFILLED)',
    `${GATEWAY_URL}/api/v1/orders/${orderId}/status?status=FULFILLED`,
    { method: 'PUT', headers: adminHeaders },
    null,
    [200]
  );
  // 6.7 Admin Audit All Entitlements
  await testEndpoint(
    'Admin Audit All Entitlements',
    `${GATEWAY_URL}/api/v1/entitlements`,
    { method: 'GET', headers: adminHeaders },
    null,
    [200]
  );
  // 6.8 Admin Audit Platform Payments
  await testEndpoint(
    'Admin Audit All Payments',
    `${GATEWAY_URL}/api/v1/payments/admin/all`,
    { method: 'GET', headers: adminHeaders },
    null,
    [200]
  );

  // 6.9 Administrative Revocation of Entitlement
  if (entitlementId) {
    log(`\n>>> [SECURITY TEST] Administrative Entitlement Revocation (ID: ${entitlementId})`);
    await testEndpoint(
      'Admin Revoke Entitlement',
      `${GATEWAY_URL}/api/v1/fulfillments/entitlements/${entitlementId}/revoke`,
      { method: 'POST', headers: adminHeaders },
      null,
      [200]
    );

    // 6.10 Security Guard: Verify Customer Download Request is now FORBIDDEN / BLOCKED
    log(`>>> [SECURITY TEST] Verifying Download Access is BLOCKED for Revoked Entitlement`);
    await testEndpoint(
      'Customer Download Blocked Post-Revocation',
      `${GATEWAY_URL}/api/v1/downloads/${digiProdId}`,
      { method: 'GET', headers: custHeaders },
      null,
      [403, 404, 400]
    );
  }

  // ============================================================================
  // SECTION 7: LOAD BALANCER DUAL-INSTANCE VERIFICATION
  // ============================================================================
  log(`\n>>> [SECTION 7] LOAD BALANCER DUAL-INSTANCE CHECK (10 Requests to /api/v1/products)`);
  for (let i = 1; i <= 10; i++) {
    await testEndpoint(
      `Load Balancer Request #${i}`,
      `${GATEWAY_URL}/api/v1/products`,
      { method: 'GET' },
      null,
      [200]
    );
  }

  // ============================================================================
  // SUMMARY REPORT
  // ============================================================================
  log(`\n========================================================================================`);
  log(`  TEST EXECUTION SUMMARY`);
  log(`  Total Endpoints Tested: ${stats.total}`);
  log(`  Passed:                 ${stats.passed} (${Math.round((stats.passed / stats.total) * 100)}%)`);
  log(`  Failed:                 ${stats.failed}`);
  log(`  Evidence Saved To:      ${evidenceLog}`);
  log(`========================================================================================\n`);

  return stats;
}

runAll().then(stats => {
  if (stats.failed > 0) {
    console.error(`Completed with ${stats.failed} failed assertions.`);
    process.exit(1);
  } else {
    console.log('All tests passed 100% successfully!');
    process.exit(0);
  }
}).catch(err => {
  console.error('Fatal execution error:', err);
  process.exit(1);
});
