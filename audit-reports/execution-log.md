[2026-10-19T18:45:15.555Z] ========================================================================
[2026-10-19T18:45:15.556Z]   BYTEVAULT MEDIA — COMPREHENSIVE END-TO-END AUTOMATION EXECUTION
[2026-10-19T18:45:15.556Z]   Time: 2026-10-19T18:45:15.556Z
[2026-10-19T18:45:15.556Z] ========================================================================

[2026-10-19T18:45:15.557Z] --- PHASE 1: ACTIVE MODULES AND SERVICE HEALTH CHECKS ---
[2026-10-19T18:45:15.619Z]   Module [discovery-server]: HTTP 200 -> UP
[2026-10-19T18:45:15.630Z]   Module [api-gateway]: HTTP 404 -> UP
[2026-10-19T18:45:15.642Z]   Module [identity-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.653Z]   Module [product-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.664Z]   Module [cart-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.675Z]   Module [order-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.686Z]   Module [fulfillment-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.698Z]   Module [notification-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.709Z]   Module [payment-service]: HTTP 403 -> UP
[2026-10-19T18:45:15.717Z]   Module [frontend-portal]: HTTP 200 -> UP
[2026-10-19T18:45:15.718Z] 
--- PHASE 2: WORKFLOW A — IDENTITY & ACCESS LIFECYCLE ---
[2026-10-19T18:45:16.318Z]   [PASS] POST /api/v1/auth/login | Scenario: Valid Admin Login | Status: 200
[2026-10-19T18:45:16.319Z] [PASS] WF-A - Admin Login: Status 200, Role: ADMIN
[2026-10-19T18:45:16.891Z]   [PASS] POST /api/v1/auth/login | Scenario: Invalid Credentials | Status: 401
[2026-10-19T18:45:17.469Z]   [PASS] POST /api/v1/auth/register | Scenario: Valid Customer Registration | Status: 200
[2026-10-19T18:45:17.470Z] [PASS] WF-A - Customer Registration: Status 200, User ID: bdb7ec00-b3e9-44d0-8fbd-69af11566c29
[2026-10-19T18:45:17.480Z]   [PASS] POST /api/v1/auth/register | Scenario: Duplicate Email Registration | Status: 409
[2026-10-19T18:45:18.057Z]   [PASS] POST /api/v1/auth/login | Scenario: Valid Customer Login | Status: 200
[2026-10-19T18:45:18.070Z]   [PASS] GET /api/v1/users/me | Scenario: Get Customer Own Profile | Status: 200
[2026-10-19T18:45:18.073Z]   [PASS] GET /api/v1/users/me | Scenario: Access Profile Without Token | Status: 401
[2026-10-19T18:45:18.660Z]   [PASS] POST /api/v1/auth/register | Scenario: Valid Vendor Registration | Status: 200
[2026-10-19T18:45:18.673Z]   [PASS] GET /api/v1/users/me/vendor-profile | Scenario: Fetch Own Vendor Profile | Status: 200
[2026-10-19T18:45:18.673Z] [PASS] WF-A - Vendor Profile Initialized: Status: PENDING
[2026-10-19T18:45:18.694Z]   [PASS] POST /api/v1/products/vendor | Scenario: Product Creation by Unapproved Vendor | Status: 403
[2026-10-19T18:45:18.694Z] [PASS] WF-B - Unapproved Vendor Blocked: HTTP 403
[2026-10-19T18:45:18.694Z] 
--- PHASE 3: WORKFLOW B — ADMIN VENDOR APPROVAL & PRODUCT PUBLISHING ---
[2026-10-19T18:45:18.712Z]   [PASS] GET /api/v1/users/admin/vendors/pending | Scenario: List Pending Vendors | Status: 200
[2026-10-19T18:45:18.723Z]   [PASS] GET /api/v1/users/admin/vendors/pending | Scenario: Customer Accessing Admin API | Status: 403
[2026-10-19T18:45:18.739Z]   [PASS] POST /api/v1/users/admin/vendors/{id}/approve | Scenario: Approve Pending Vendor | Status: 200
[2026-10-19T18:45:18.739Z] [PASS] WF-B - Admin Vendor Approval: Status: 200
[2026-10-19T18:45:18.764Z]   [PASS] POST /api/v1/products/vendor | Scenario: Create & Publish Digital Product | Status: 201
[2026-10-19T18:45:18.764Z] [PASS] WF-B - Digital Product Publishing: Status 201, ID: 6c076cb7-f841-49b3-b963-6c4c2e551ba1
[2026-10-19T18:45:18.790Z]   [PASS] POST /api/v1/products/vendor | Scenario: Create & Publish Physical Product | Status: 201
[2026-10-19T18:45:18.790Z] [PASS] WF-B - Physical Product Publishing: Status 201, ID: 83a0f9dc-a94d-4f17-a0a2-f8dde9ba30a8
[2026-10-19T18:45:18.815Z]   [PASS] GET /api/v1/products/vendor/my-products | Scenario: List Vendor Own Products | Status: 200
[2026-10-19T18:45:18.852Z]   [PASS] GET /api/v1/products | Scenario: Public Catalog Fetch | Status: 200
[2026-10-19T18:45:18.864Z]   [PASS] GET /api/v1/products/{id} | Scenario: Get Product Details by ID | Status: 200
[2026-10-19T18:45:18.889Z]   [PASS] GET /api/v1/products/{id} | Scenario: Nonexistent Product ID | Status: 404
[2026-10-19T18:45:18.889Z] 
--- PHASE 4: WORKFLOW C — CART & CHECKOUT OPERATIONS ---
[2026-10-19T18:45:18.911Z]   [PASS] GET /api/v1/cart | Scenario: Get Customer Cart (Initial) | Status: 200
[2026-10-19T18:45:18.945Z]   [PASS] POST /api/v1/cart/items | Scenario: Add Item to Cart | Status: 200
[2026-10-19T18:45:18.945Z] [PASS] WF-C - Cart Add Item: Cart items: 1
[2026-10-19T18:45:18.967Z]   [PASS] PUT /api/v1/cart/items/{productId}?quantity=2 | Scenario: Digital Item Quantity Cap Validation (> 1 Rejected) | Status: 400
[2026-10-19T18:45:18.967Z] [PASS] WF-C - Digital Product Cart Restriction: Status: 400
[2026-10-19T18:45:19.024Z]   [PASS] PUT /api/v1/cart/items/{productId} | Scenario: Update Physical Item Quantity to 2 | Status: 200
[2026-10-19T18:45:19.062Z]   [PASS] DELETE /api/v1/cart | Scenario: Clear Entire Cart | Status: 200
[2026-10-19T18:45:19.099Z]   [PASS] POST /api/v1/orders | Scenario: Create Digital Order with Authoritative Pricing | Status: 200
[2026-10-19T18:45:19.099Z] [PASS] WF-C - Authoritative Price Enforcement: Actual total: ₹75
[2026-10-19T18:45:19.117Z]   [PASS] GET /api/v1/orders/{id} | Scenario: Fetch Order Details | Status: 200
[2026-10-19T18:45:19.138Z]   [PASS] GET /api/v1/orders | Scenario: Customer Order History | Status: 200
[2026-10-19T18:45:19.138Z] 
--- PHASE 5: WORKFLOW D — PAYMENT CREATION & VERIFICATION ---
[2026-10-19T18:45:19.158Z]   [PASS] POST /api/v1/payments/create-order | Scenario: Create Payment Order | Status: 200
[2026-10-19T18:45:19.159Z] [PASS] WF-D - Payment Order Created: Razorpay Order: order_mock_0ee85d5c
[2026-10-19T18:45:19.253Z]   [PASS] POST /api/v1/payments/verify | Scenario: Payment Verification in Test Mode | Status: 200
[2026-10-19T18:45:19.253Z] [PASS] WF-D - Payment Verification & Order State Transition: Payment verify status: 200
[2026-10-19T18:45:19.266Z] [PASS] WF-D - Order Status Verification: Current status: PAID
[2026-10-19T18:45:19.280Z]   [PASS] GET /api/v1/payments/my-transactions | Scenario: Get Customer Payment Transactions | Status: 200
[2026-10-19T18:45:19.298Z]   [PASS] GET /api/v1/payments/vendor/earnings | Scenario: Get Vendor Earnings & Commission Split | Status: 200
[2026-10-19T18:45:19.298Z] [PASS] WF-D - Vendor Commission Ledger Calculation: Net: ₹67.5, Comm: ₹undefined
[2026-10-19T18:45:19.310Z]   [PASS] GET /api/v1/payments/vendor/earnings?vendorId={other} | Scenario: Cross-Vendor Earnings Access (IDOR Test) | Status: 403
[2026-10-19T18:45:19.310Z] 
--- PHASE 6: WORKFLOW E — ENTITLEMENTS & SECURE DOWNLOADS ---
[2026-10-19T18:45:19.350Z]   [PASS] GET /api/v1/fulfillments/my-entitlements | Scenario: Get Customer Digital Entitlements | Status: 200
[2026-10-19T18:45:19.350Z] [PASS] WF-E - Digital Entitlement Provisioning: Matching entitlement: 84114d3b-fafe-4260-aa9e-9ce5a21fc4b4, status: ACTIVE
[2026-10-19T18:45:19.377Z]   [PASS] GET /api/v1/downloads/{productId} | Scenario: Generate Presigned Download URL | Status: 200
[2026-10-19T18:45:19.377Z] [PASS] WF-E - Presigned Download URL Generation: URL: http://localhost:8080/api/v1/products/assets/download?key=assets/tf-aws-kit-1792435515552.zip&token=sig_5085368a-9f7e-3b35-81fa-76cd379ca9e9&expiry=15m
[2026-10-19T18:45:19.400Z]   [PASS] GET /api/v1/products/assets/download | Scenario: Stream Binary Asset File | Status: 200
[2026-10-19T18:45:19.400Z] [PASS] WF-E - Binary File Retrieval: Streamed bytes: 138
[2026-10-19T18:45:20.003Z]   [PASS] GET /api/v1/downloads/{productId} | Scenario: Unauthorized Asset Download Attempt | Status: 400
[2026-10-19T18:45:20.003Z] [PASS] WF-E - Customer Digital Asset Isolation: HTTP 400
[2026-10-19T18:45:20.003Z] 
--- PHASE 7: WORKFLOW G — NOTIFICATION DISPATCHES ---
[2026-10-19T18:45:20.017Z]   [PASS] POST /api/v1/notifications/email/verification | Scenario: Dispatch Email Verification Notification | Status: 200
[2026-10-19T18:45:20.030Z]   [PASS] POST /api/v1/notifications/email/order-confirmation | Scenario: Dispatch Order Confirmation Email | Status: 200
[2026-10-19T18:45:20.043Z]   [PASS] POST /api/v1/notifications/email/entitlement-ready | Scenario: Dispatch Entitlement Ready Email | Status: 200
[2026-10-19T18:45:20.056Z]   [PASS] POST /api/v1/notifications/email/password-reset | Scenario: Dispatch Password Reset Email | Status: 200
[2026-10-19T18:45:20.056Z] [PASS] WF-G - Notification Service Dispatches: All 4 email templates dispatched successfully
[2026-10-19T18:45:20.056Z] 
--- PHASE 8: WORKFLOW H — GATEWAY PERIMETER SECURITY & INTERNAL ROUTE BARRIERS ---
[2026-10-19T18:45:20.059Z]   [PASS] POST /api/v1/internal/fulfill | Scenario: Access Internal Endpoint Through Gateway | Status: 403
[2026-10-19T18:45:20.072Z]   [PASS] GET /api/v1/users/me | Scenario: Gateway Strips Injected Identity Headers | Status: 200
[2026-10-19T18:45:20.072Z] [PASS] WF-H - Perimeter Header Sanitation: Authenticated user ID: bdb7ec00-b3e9-44d0-8fbd-69af11566c29
[2026-10-19T18:45:20.074Z]   [PASS] GET http://localhost:8088/api/v1/fulfillments/my-entitlements | Scenario: Downstream Direct Ingress Protection | Status: 403
[2026-10-19T18:45:20.074Z] [PASS] WF-H - Zero-Trust Downstream Isolation: Status: 403
[2026-10-19T18:45:20.074Z] 
--- PHASE 9: WORKFLOW I — FRONTEND INTEGRATION ---
[2026-10-19T18:45:20.081Z] [PASS] WF-I - Frontend Portal Availability: HTTP 200, Title present: true
[2026-10-19T18:45:20.086Z] [PASS] WF-I - Frontend API Client Integration: Config status: true
[2026-10-19T18:45:20.086Z] 
--- PHASE 10: GENERATING STRUCTURED AUDIT ARTIFACTS ---