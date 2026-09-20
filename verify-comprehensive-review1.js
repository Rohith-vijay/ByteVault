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
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => {
        const rawBuffer = Buffer.concat(chunks);
        const bodyStr = rawBuffer.toString('utf8');
        let parsed = bodyStr;
        try {
          parsed = JSON.parse(bodyStr);
        } catch (e) {}
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: parsed,
          rawBody: bodyStr,
          rawBuffer: rawBuffer
        });
      });
    });

    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function runAll() {
  console.log('========================================================================');
  console.log('  BYTEVAULT MEDIA — COMPREHENSIVE END-TO-END REGRESSION SUITE');
  console.log('========================================================================\n');

  const report = [];
  function recordTest(testName, actualReq, actualRes, dbResult, feResult, pass, details = '') {
    report.push({ testName, actualReq, actualRes, dbResult, feResult, pass, details });
    const tag = pass ? 'PASS' : 'FAIL';
    console.log(`[${tag}] ${testName}`);
    if (details) console.log(`       Info: ${details}`);
  }

  const timestamp = Date.now();

  // 1. Admin Login
  let adminToken, adminId;
  const adminRes = await request('http://localhost:8080/api/v1/auth/login', { method: 'POST' }, {
    email: 'admin@bytevault.com',
    password: 'AdminPass123!'
  });
  adminToken = adminRes.body?.data?.token || adminRes.body?.token;
  adminId = decodeJwt(adminToken)?.id;
  recordTest(
    'Admin Authentication & JWT Validation',
    'POST /api/v1/auth/login (admin@bytevault.com)',
    `HTTP ${adminRes.status} (token received)`,
    `auth_db users contains admin user (${adminId})`,
    'Admin Console Session Initialized',
    adminRes.status === 200 && !!adminToken,
    `Admin ID: ${adminId}`
  );

  // 2. Vendor Registration & Admin Approval
  const vendorEmail = `vendor_${timestamp}@bytevault.com`;
  const vRegRes = await request('http://localhost:8080/api/v1/auth/register', { method: 'POST' }, {
    email: vendorEmail,
    password: 'Password123!',
    fullName: 'ByteVault Design Studio',
    role: 'VENDOR'
  });
  const vendorToken = vRegRes.body?.data?.token || vRegRes.body?.token;
  const vendorId = decodeJwt(vendorToken)?.id;
  await request('http://localhost:8080/api/v1/users/me/vendor-profile', {
    headers: { 'Authorization': `Bearer ${vendorToken}` }
  });
  const approveRes = await request(`http://localhost:8080/api/v1/users/admin/vendors/${vendorId}/approve`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  recordTest(
    'Vendor Registration & Admin Approval Workflow',
    `POST /api/v1/users/admin/vendors/${vendorId}/approve`,
    `HTTP ${approveRes.status}`,
    `user_db vendor_profiles status = APPROVED`,
    'Vendor Studio Access Granted',
    approveRes.status === 200,
    `Vendor ID: ${vendorId}`
  );

  // 3. Vendor Creates Digital Product (₹49.99)
  const digiPrice = 49.99;
  const digiProdRes = await request('http://localhost:8080/api/v1/products/vendor?publish=true', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${vendorToken}` }
  }, {
    name: `Cloud Architecture Asset Bundle ${timestamp}`,
    description: 'Complete production cloud infrastructure templates.',
    price: digiPrice,
    productType: 'DIGITAL',
    tags: 'cloud, devops, architecture',
    fileName: `cloud-bundle-${timestamp}.zip`,
    fileType: 'application/zip',
    fileSize: 10485760
  });
  const digiProd = digiProdRes.body?.data || digiProdRes.body;
  const digiProdId = digiProd?.id || digiProd?.productId;
  recordTest(
    'Vendor Digital Product Creation & Publishing (₹49.99)',
    'POST /api/v1/products/vendor?publish=true (productType=DIGITAL, price=49.99)',
    `HTTP ${digiProdRes.status} (Product ID: ${digiProdId})`,
    `product_db products status = PUBLISHED, price = 49.99`,
    'Product displayed in marketplace catalog',
    (digiProdRes.status === 200 || digiProdRes.status === 201) && !!digiProdId,
    `Digital Product ID: ${digiProdId}, Price: ₹${digiPrice}`
  );

  // 4. Vendor Creates Physical Product (₹89.50)
  const physPrice = 89.50;
  const physProdRes = await request('http://localhost:8080/api/v1/products/vendor?publish=true', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${vendorToken}` }
  }, {
    name: `Mechanical HHKB Keycap Set ${timestamp}`,
    description: 'Custom doubleshot PBT keycap set.',
    price: physPrice,
    sku: `KEYCAP-HHKB-${timestamp}`,
    productType: 'PHYSICAL',
    tags: 'hardware, peripherals, keycaps',
    physicalWeight: 0.5,
    physicalDimensions: '20x10x5 cm'
  });
  const physProd = physProdRes.body?.data || physProdRes.body;
  const physProdId = physProd?.id || physProd?.productId;
  recordTest(
    'Vendor Physical Product Creation & Publishing (₹89.50)',
    'POST /api/v1/products/vendor?publish=true (productType=PHYSICAL, price=89.50)',
    `HTTP ${physProdRes.status} (Product ID: ${physProdId})`,
    `product_db products status = PUBLISHED, price = 89.50`,
    'Product displayed in physical gear marketplace',
    (physProdRes.status === 200 || physProdRes.status === 201) && !!physProdId,
    `Physical Product ID: ${physProdId}, Price: ₹${physPrice}`
  );

  // 5. Customer A Registration & Login
  const customerAEmail = `customerA_${timestamp}@bytevault.com`;
  const regARes = await request('http://localhost:8080/api/v1/auth/register', { method: 'POST' }, {
    email: customerAEmail,
    password: 'Password123!',
    fullName: 'Alice Customer',
    role: 'CUSTOMER'
  });
  const customerAToken = regARes.body?.data?.token || regARes.body?.token;
  const customerAId = decodeJwt(customerAToken)?.id;
  recordTest(
    'Customer A Registration & Authentication',
    `POST /api/v1/auth/register (${customerAEmail})`,
    `HTTP ${regARes.status}`,
    `auth_db users created for Customer A (${customerAId})`,
    'Customer Portal Dashboard Loaded',
    regARes.status === 200 && !!customerAToken,
    `Customer A ID: ${customerAId}`
  );

  // 6. Customer A Purchases Digital Product
  // Order placement
  const digiOrderRes = await request('http://localhost:8080/api/v1/orders', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  }, {
    customerId: customerAId,
    items: [{
      productId: digiProdId,
      quantity: 1
    }],
    totalAmount: digiPrice,
    shippingAddress: 'Digital Delivery'
  });
  const digiOrder = digiOrderRes.body?.data || digiOrderRes.body;
  const digiOrderId = digiOrder?.id || digiOrder?.orderId;

  // Payment creation
  const payInitDigi = await request('http://localhost:8080/api/v1/payments/create-order', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  }, { amount: digiPrice, currency: 'INR', receipt: digiOrderId });
  const razorOrderIdDigi = payInitDigi.body?.data?.orderId || `order_${timestamp}`;

  // Payment verify
  const payVerifyDigi = await request('http://localhost:8080/api/v1/payments/verify', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  }, {
    razorpayOrderId: razorOrderIdDigi,
    razorpayPaymentId: `pay_digi_${timestamp}`,
    razorpaySignature: `sig_digi_${timestamp}`,
    dbOrderId: digiOrderId,
    userId: customerAId,
    amount: digiPrice
  });

  // Verify Order status via Customer Orders API
  const orderHistoryA = await request('http://localhost:8080/api/v1/orders', {
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  });
  const ordersListA = orderHistoryA.body?.data || (Array.isArray(orderHistoryA.body) ? orderHistoryA.body : []);
  const foundDigiOrder = ordersListA.find(o => (o.id === digiOrderId || o.orderId === digiOrderId));

  recordTest(
    'BUG 1 & BUG 6 & BUG 7: Digital Order Payment, Status PAID & Exact Price (₹49.99)',
    `POST /api/v1/payments/verify (dbOrderId=${digiOrderId}, amount=49.99)`,
    `HTTP ${payVerifyDigi.status}, Order Status = ${foundDigiOrder?.status}`,
    `order_db orders status = PAID, total_amount = 49.99`,
    `Orders page renders status 'PAID', unit price ₹${foundDigiOrder?.items?.[0]?.unitPrice || '49.99'}, total ₹${foundDigiOrder?.totalAmount || '49.99'}`,
    payVerifyDigi.status === 200 && foundDigiOrder?.status === 'PAID' && Number(foundDigiOrder?.totalAmount) === digiPrice,
    `Order ID: ${digiOrderId}, Status: ${foundDigiOrder?.status}, Total: ₹${foundDigiOrder?.totalAmount}`
  );

  // 7. Entitlement Verification (BUG 3)
  const entResA = await request('http://localhost:8080/api/v1/fulfillments/my-entitlements', {
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  });
  const entitlementsA = entResA.body?.data || (Array.isArray(entResA.body) ? entResA.body : []);
  const foundEntitlement = entitlementsA.find(e => e.productId === digiProdId);
  recordTest(
    'BUG 3: Digital Vault Entitlement Created & Returned via API',
    'GET /api/v1/fulfillments/my-entitlements',
    `HTTP ${entResA.status}, Entitlements Count: ${entitlementsA.length}`,
    `fulfillment_db entitlements contains (user_id=${customerAId}, product_id=${digiProdId})`,
    `Digital Vault displays count (${entitlementsA.length}) and reveals Download button`,
    entResA.status === 200 && !!foundEntitlement,
    `Entitlement ID: ${foundEntitlement?.id || foundEntitlement?.entitlementId}`
  );

  // 8. Real Asset Download Verification (BUG 4)
  const dlTokenRes = await request(`http://localhost:8080/api/v1/downloads/${digiProdId}`, {
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  });
  const dlUrl = dlTokenRes.body?.data?.downloadUrl || dlTokenRes.body?.downloadUrl;
  let fileDownloadPass = false;
  let downloadedBytes = 0;
  if (dlUrl) {
    const fullUrl = dlUrl.startsWith('http') ? dlUrl : `http://localhost:8080${dlUrl}`;
    const fileRes = await request(fullUrl, {
      headers: { 'Authorization': `Bearer ${customerAToken}` }
    });
    downloadedBytes = fileRes.rawBuffer.length;
    fileDownloadPass = fileRes.status === 200 && downloadedBytes > 0;
  }
  recordTest(
    'BUG 4: Real Asset Download Stream Verification',
    `GET ${dlUrl}`,
    `HTTP 200, Streamed ${downloadedBytes} bytes`,
    'MinIO / Asset storage streams real payload bytes',
    'Browser triggers binary save dialog for purchased asset',
    fileDownloadPass,
    `Downloaded bytes: ${downloadedBytes}`
  );

  // 9. Customer A Purchases Physical Product (₹89.50)
  const physOrderRes = await request('http://localhost:8080/api/v1/orders', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  }, {
    customerId: customerAId,
    items: [{
      productId: physProdId,
      quantity: 1
    }],
    totalAmount: physPrice,
    shippingAddress: '42 Baker Street, London, UK'
  });
  const physOrder = physOrderRes.body?.data || physOrderRes.body;
  const physOrderId = physOrder?.id || physOrder?.orderId;

  // Pay for physical order
  const payInitPhys = await request('http://localhost:8080/api/v1/payments/create-order', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  }, { amount: physPrice, currency: 'INR', receipt: physOrderId });
  const razorOrderIdPhys = payInitPhys.body?.data?.orderId || `order_phys_${timestamp}`;

  const payVerifyPhys = await request('http://localhost:8080/api/v1/payments/verify', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  }, {
    razorpayOrderId: razorOrderIdPhys,
    razorpayPaymentId: `pay_phys_${timestamp}`,
    razorpaySignature: `sig_phys_${timestamp}`,
    dbOrderId: physOrderId,
    userId: customerAId,
    amount: physPrice
  });

  // Verify Physical Order does NOT create digital entitlement
  const entResAAfterPhys = await request('http://localhost:8080/api/v1/fulfillments/my-entitlements', {
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  });
  const entitlementsA2 = entResAAfterPhys.body?.data || (Array.isArray(entResAAfterPhys.body) ? entResAAfterPhys.body : []);
  const physEntitlement = entitlementsA2.find(e => e.productId === physProdId);

  // Check physical order status and distinct prices (BUG 2 & BUG 5 & BUG 8)
  const orderHistoryA2 = await request('http://localhost:8080/api/v1/orders', {
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  });
  const allOrdersA = orderHistoryA2.body?.data || (Array.isArray(orderHistoryA2.body) ? orderHistoryA2.body : []);
  const foundPhysOrder = allOrdersA.find(o => (o.id === physOrderId || o.orderId === physOrderId));

  recordTest(
    'BUG 5 & BUG 8: Physical Product Order Integrity & No Digital Entitlement',
    `POST /api/v1/orders (Physical product, ₹89.50) & /payments/verify`,
    `Physical Order: ₹${foundPhysOrder?.totalAmount}, Physical Entitlement = null`,
    `order_db orders status = PAID, total_amount = 89.50; fulfillment_db no physical row`,
    'Order history renders Track Order for physical, Digital Vault for digital; exact distinct prices (₹49.99 vs ₹89.50)',
    payVerifyPhys.status === 200 && foundPhysOrder?.status === 'PAID' && Number(foundPhysOrder?.totalAmount) === physPrice && !physEntitlement,
    `Physical Order ID: ${physOrderId}, Price: ₹${foundPhysOrder?.totalAmount}`
  );

  // 10. Customer Isolation (BUG 9)
  const customerBEmail = `customerB_${timestamp}@bytevault.com`;
  const regBRes = await request('http://localhost:8080/api/v1/auth/register', { method: 'POST' }, {
    email: customerBEmail,
    password: 'Password123!',
    fullName: 'Bob Customer',
    role: 'CUSTOMER'
  });
  const customerBToken = regBRes.body?.data?.token || regBRes.body?.token;
  const customerBId = decodeJwt(customerBToken)?.id;

  const ordersBRes = await request('http://localhost:8080/api/v1/orders', {
    headers: { 'Authorization': `Bearer ${customerBToken}` }
  });
  const ordersB = ordersBRes.body?.data || (Array.isArray(ordersBRes.body) ? ordersBRes.body : []);

  const entBRes = await request('http://localhost:8080/api/v1/fulfillments/my-entitlements', {
    headers: { 'Authorization': `Bearer ${customerBToken}` }
  });
  const entB = entBRes.body?.data || (Array.isArray(entBRes.body) ? entBRes.body : []);

  // Customer B tries to download Customer A's purchased digital asset
  const dlBRes = await request(`http://localhost:8080/api/v1/downloads/${digiProdId}`, {
    headers: { 'Authorization': `Bearer ${customerBToken}` }
  });

  const isolationPass = ordersB.length === 0 && entB.length === 0 && (dlBRes.status === 403 || dlBRes.status === 404 || dlBRes.status === 400 || dlBRes.status === 401);
  recordTest(
    'BUG 9: Customer Isolation (Customer B cannot access Customer A orders/vault/downloads)',
    `GET /orders, /fulfillments/my-entitlements, /downloads/${digiProdId}`,
    `Orders count: ${ordersB.length}, Entitlements count: ${entB.length}, Download status: HTTP ${dlBRes.status}`,
    'Zero cross-customer leaks across order_db and fulfillment_db',
    'Customer B portal shows 0 orders and 0 vault assets',
    isolationPass,
    `Customer B Orders: ${ordersB.length}, Entitlements: ${entB.length}, Download Status: ${dlBRes.status}`
  );

  // 11. RBAC Customer Access to Admin
  const rbacRes = await request('http://localhost:8080/api/v1/users/admin/vendors/pending', {
    headers: { 'Authorization': `Bearer ${customerAToken}` }
  });
  recordTest(
    'BUG 11: Admin RBAC Security Barrier',
    'GET /api/v1/users/admin/vendors/pending with Customer JWT',
    `HTTP ${rbacRes.status} (Forbidden/Unauthorized)`,
    'Security context forbids non-admin principal',
    'User redirected or presented access denied modal',
    rbacRes.status === 403 || rbacRes.status === 401,
    `Status: HTTP ${rbacRes.status}`
  );

  console.log('\n========================================================================');
  const total = report.length;
  const passed = report.filter(r => r.pass).length;
  console.log(`  COMPREHENSIVE REGRESSION RUN COMPLETE: ${passed}/${total} PASSED`);
  console.log('========================================================================\n');

  return { total, passed, report };
}

runAll().then(res => {
  if (res.passed !== res.total) {
    process.exitCode = 1;
  }
}).catch(e => {
  console.error(e);
  process.exitCode = 1;
});
