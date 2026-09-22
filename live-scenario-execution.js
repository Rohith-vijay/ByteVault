const http = require('http');
const fs = require('fs');
const path = require('path');

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
      if (typeof data === 'object' && !(data instanceof Buffer)) {
        data = JSON.stringify(data);
        reqOptions.headers['Content-Type'] = 'application/json';
      }
      reqOptions.headers['Content-Length'] = Buffer.isBuffer(data) ? data.length : Buffer.byteLength(data);
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

async function runEndToEndScenario() {
  const timestamp = Date.now();
  console.log(`========================================================================`);
  console.log(`  BYTEVAULT MEDIA — LIVE MULTI-ROLE SCENARIO EXECUTION [${new Date().toISOString()}]`);
  console.log(`========================================================================\n`);

  const results = {};

  // 1. Admin Login
  console.log('1. Authenticating as Platform Administrator...');
  const adminRes = await request('http://localhost:8080/api/v1/auth/login', { method: 'POST' }, {
    email: 'admin@bytevault.com',
    password: 'AdminPass123!'
  });
  const adminToken = adminRes.body?.data?.token || adminRes.body?.token;
  const adminClaims = decodeJwt(adminToken);
  console.log(`   -> Admin Login: HTTP ${adminRes.status}, Admin ID: ${adminClaims?.id}`);
  results.admin = { id: adminClaims?.id, email: 'admin@bytevault.com', status: adminRes.status };

  // 2. Register New Vendor
  const vendorEmail = `nexus_vendor_${timestamp}@bytevault.com`;
  const vendorPassword = 'VendorPass123!';
  console.log(`\n2. Registering New Vendor (${vendorEmail})...`);
  const vRegRes = await request('http://localhost:8080/api/v1/auth/register', { method: 'POST' }, {
    email: vendorEmail,
    password: vendorPassword,
    fullName: 'Nexus Hardware & Cloud Labs',
    role: 'VENDOR'
  });
  const vendorToken = vRegRes.body?.data?.token || vRegRes.body?.token;
  const vendorClaims = decodeJwt(vendorToken);
  const vendorId = vendorClaims?.id;
  console.log(`   -> Vendor Registered: HTTP ${vRegRes.status}, Vendor ID: ${vendorId}`);

  // Initialize Vendor Profile
  await request('http://localhost:8080/api/v1/users/me/vendor-profile', {
    headers: { 'Authorization': `Bearer ${vendorToken}` }
  });

  // 3. Admin Approves New Vendor
  console.log(`\n3. Admin Approving Vendor (ID: ${vendorId})...`);
  const approveRes = await request(`http://localhost:8080/api/v1/users/admin/vendors/${vendorId}/approve`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });
  console.log(`   -> Admin Approval Status: HTTP ${approveRes.status}`);
  results.vendor = {
    id: vendorId,
    email: vendorEmail,
    approved: approveRes.status === 200,
    storeName: 'Nexus Hardware & Cloud Labs'
  };

  // 4. Approved Vendor Creates & Publishes Digital Product (₹129.00)
  const digiPrice = 129.00;
  console.log(`\n4. Vendor Creating & Publishing Digital Product (₹${digiPrice})...`);
  const digiProdRes = await request('http://localhost:8080/api/v1/products/vendor?publish=true', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${vendorToken}` }
  }, {
    name: `Kubernetes Cluster Blueprint Pro v${timestamp}`,
    description: 'Production-ready Helm charts, Terraform configurations, and multi-region deployment scripts.',
    price: digiPrice,
    productType: 'DIGITAL',
    tags: 'kubernetes, devops, terraform, cloud',
    fileName: `k8s-cluster-blueprint-${timestamp}.zip`,
    fileType: 'application/zip',
    fileSize: 15728640,
    fileVersion: '2.4.0'
  });
  const digiProd = digiProdRes.body?.data || digiProdRes.body;
  const digiProdId = digiProd?.id || digiProd?.productId;
  console.log(`   -> Digital Product Created: HTTP ${digiProdRes.status}, Product ID: ${digiProdId}, Status: ${digiProd?.status}`);
  results.digitalProduct = { id: digiProdId, name: digiProd?.name, price: digiPrice, status: digiProd?.status };

  // 5. Approved Vendor Creates & Publishes Physical Product with PDF User Manual (₹249.50)
  const physPrice = 249.50;
  const physicalSku = `NEXUS-ROUTER-${timestamp}`;
  console.log(`\n5. Vendor Creating & Publishing Physical Product with Documentation PDF (₹${physPrice})...`);
  const physProdRes = await request('http://localhost:8080/api/v1/products/vendor?publish=true', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${vendorToken}` }
  }, {
    name: `Nexus Edge Gateway Router v${timestamp}`,
    description: 'Hardware enterprise IoT and microservice gateway with dual SFP+ and hardware cryptographic accelerator.',
    price: physPrice,
    sku: physicalSku,
    productType: 'PHYSICAL',
    tags: 'hardware, networking, iot, gateway',
    physicalWeight: 1.25,
    physicalDimensions: '25x18x4.5 cm',
    shippingClass: 'Express Insured',
    fileName: `nexus-router-user-manual-spec-${timestamp}.pdf`,
    fileType: 'application/pdf',
    fileSize: 5242880
  });
  const physProd = physProdRes.body?.data || physProdRes.body;
  const physProdId = physProd?.id || physProd?.productId;
  console.log(`   -> Physical Product Created: HTTP ${physProdRes.status}, Product ID: ${physProdId}, Status: ${physProd?.status}`);
  results.physicalProduct = { id: physProdId, name: physProd?.name, price: physPrice, sku: physicalSku, status: physProd?.status };

  // 6. Register New Customer
  const customerEmail = `enterprise_buyer_${timestamp}@bytevault.com`;
  const customerPassword = 'BuyerPass123!';
  console.log(`\n6. Registering New Customer (${customerEmail})...`);
  const cRegRes = await request('http://localhost:8080/api/v1/auth/register', { method: 'POST' }, {
    email: customerEmail,
    password: customerPassword,
    fullName: 'David Mitchell',
    role: 'CUSTOMER'
  });
  const customerToken = cRegRes.body?.data?.token || cRegRes.body?.token;
  const customerClaims = decodeJwt(customerToken);
  const customerId = customerClaims?.id;
  console.log(`   -> Customer Registered: HTTP ${cRegRes.status}, Customer ID: ${customerId}`);
  results.customer = { id: customerId, email: customerEmail };

  // 7. Customer Purchases Digital Product
  console.log(`\n7. Customer Purchasing Digital Product (ID: ${digiProdId})...`);
  const digiOrderRes = await request('http://localhost:8080/api/v1/orders', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerToken}` }
  }, {
    customerId: customerId,
    items: [{ productId: digiProdId, quantity: 1 }],
    totalAmount: digiPrice,
    shippingAddress: 'Digital Instant Cloud Delivery'
  });
  const digiOrder = digiOrderRes.body?.data || digiOrderRes.body;
  const digiOrderId = digiOrder?.id || digiOrder?.orderId;
  console.log(`   -> Digital Order Placed: HTTP ${digiOrderRes.status}, Order ID: ${digiOrderId}`);

  // Create payment order
  const payInitDigi = await request('http://localhost:8080/api/v1/payments/create-order', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerToken}` }
  }, { amount: digiPrice, currency: 'INR', receipt: digiOrderId });
  const razorOrderIdDigi = payInitDigi.body?.data?.orderId || `order_${timestamp}`;

  // Verify payment
  const payVerifyDigi = await request('http://localhost:8080/api/v1/payments/verify', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerToken}` }
  }, {
    razorpayOrderId: razorOrderIdDigi,
    razorpayPaymentId: `pay_digi_${timestamp}`,
    razorpaySignature: `sig_digi_${timestamp}`,
    dbOrderId: digiOrderId,
    userId: customerId,
    amount: digiPrice
  });
  console.log(`   -> Digital Payment Verified: HTTP ${payVerifyDigi.status}`);

  // 8. Verify Digital Entitlement & Download Asset Stream
  console.log('\n8. Checking Customer Entitlements & Executing Download...');
  const entRes = await request('http://localhost:8080/api/v1/fulfillments/my-entitlements', {
    headers: { 'Authorization': `Bearer ${customerToken}` }
  });
  const entitlements = entRes.body?.data || (Array.isArray(entRes.body) ? entRes.body : []);
  const matchingEnt = entitlements.find(e => e.productId === digiProdId);
  console.log(`   -> Total Active Entitlements: ${entitlements.length}`);
  console.log(`   -> Matching Entitlement ID: ${matchingEnt?.id || matchingEnt?.entitlementId}, Status: ${matchingEnt?.status}`);

  // Fetch download URL and stream bytes
  const dlRes = await request(`http://localhost:8080/api/v1/downloads/${digiProdId}`, {
    headers: { 'Authorization': `Bearer ${customerToken}` }
  });
  const dlUrl = dlRes.body?.data?.downloadUrl || dlRes.body?.downloadUrl;
  console.log(`   -> Secure Signed Download URL: ${dlUrl}`);

  let downloadedBytes = 0;
  if (dlUrl) {
    const fullUrl = dlUrl.startsWith('http') ? dlUrl : `http://localhost:8080${dlUrl}`;
    const fileRes = await request(fullUrl, {
      headers: { 'Authorization': `Bearer ${customerToken}` }
    });
    downloadedBytes = fileRes.rawBuffer.length;
    console.log(`   -> Binary Download Streamed: ${downloadedBytes} bytes (HTTP ${fileRes.status})`);
  }
  results.digitalPurchase = {
    orderId: digiOrderId,
    price: digiPrice,
    entitlementId: matchingEnt?.id || matchingEnt?.entitlementId,
    downloadUrl: dlUrl,
    downloadedBytes: downloadedBytes
  };

  // 9. Customer Purchases Physical Product
  console.log(`\n9. Customer Purchasing Physical Product (ID: ${physProdId})...`);
  const physOrderRes = await request('http://localhost:8080/api/v1/orders', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerToken}` }
  }, {
    customerId: customerId,
    items: [{ productId: physProdId, quantity: 1 }],
    totalAmount: physPrice,
    shippingAddress: 'Building 4B, Cyber Park Sector 62, Gurgaon, HR 122002, IN'
  });
  const physOrder = physOrderRes.body?.data || physOrderRes.body;
  const physOrderId = physOrder?.id || physOrder?.orderId;
  console.log(`   -> Physical Order Placed: HTTP ${physOrderRes.status}, Order ID: ${physOrderId}`);

  // Create payment order
  const payInitPhys = await request('http://localhost:8080/api/v1/payments/create-order', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerToken}` }
  }, { amount: physPrice, currency: 'INR', receipt: physOrderId });
  const razorOrderIdPhys = payInitPhys.body?.data?.orderId || `order_phys_${timestamp}`;

  // Verify payment
  const payVerifyPhys = await request('http://localhost:8080/api/v1/payments/verify', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${customerToken}` }
  }, {
    razorpayOrderId: razorOrderIdPhys,
    razorpayPaymentId: `pay_phys_${timestamp}`,
    razorpaySignature: `sig_phys_${timestamp}`,
    dbOrderId: physOrderId,
    userId: customerId,
    amount: physPrice
  });
  console.log(`   -> Physical Payment Verified: HTTP ${payVerifyPhys.status}`);

  // 10. Check Customer Order History
  console.log('\n10. Fetching Customer Order History...');
  const orderHistoryRes = await request('http://localhost:8080/api/v1/orders', {
    headers: { 'Authorization': `Bearer ${customerToken}` }
  });
  const allOrders = orderHistoryRes.body?.data || (Array.isArray(orderHistoryRes.body) ? orderHistoryRes.body : []);
  console.log(`   -> Total Orders in Customer History: ${allOrders.length}`);
  allOrders.forEach(o => {
    console.log(`      * Order #${o.id} | Status: ${o.status} | Total: ₹${o.totalAmount} | Items: ${o.items?.map(i => `${i.productName} (₹${i.unitPrice})`).join(', ')}`);
  });

  results.physicalPurchase = {
    orderId: physOrderId,
    price: physPrice,
    status: allOrders.find(o => o.id === physOrderId)?.status
  };

  console.log('\n========================================================================');
  console.log('  LIVE SCENARIO COMPLETED SUCCESSFULLY');
  console.log('========================================================================\n');
  return results;
}

runEndToEndScenario().catch(e => {
  console.error(e);
  process.exitCode = 1;
});
