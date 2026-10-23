# ByteVault Media — 3-Person Team Live Demonstration Script (Review 2)

**Time Allocation:** ~15–20 minutes total (~5–6 minutes per team member)  
**Primary Interface:** API Gateway Swagger UI (`http://localhost:8080/swagger-ui.html`) + PowerShell / Terminal  
**Service Discovery Console:** Eureka Dashboard (`http://localhost:8761`)  
**Prerequisites:** All 8 active microservices + Gateway running via `node start-active-stack.js`. Database seeded via `tools/seed-demo.ps1`.

---

## Team Roles & Ownership Breakdown

- **Member 1 (Infrastructure, Gateway & Security):** Eureka Service Discovery, Spring Cloud Gateway, Swagger UI aggregation, JWT issuance, RBAC, perimeter security filter.
- **Member 2 (Catalog, Cart & Orders Domain):** Vendor onboarding workflow, write-gate verification, digital catalog querying, shopping cart operations, digital order placement.
- **Member 3 (Payments, Fulfillment & Reliability):** Razorpay payment verification, 90/10 vendor royalty accounting, synchronous fulfillment, secure signed asset downloads, outbox retry fault tolerance.

---

## Demonstration Script

```
   ┌───────────────────────────────────────────────────────────────┐
   │                    EUREKA DASHBOARD (:8761)                   │
   │   8 Active Applications: API-GATEWAY, IDENTITY-SERVICE,       │
   │   PRODUCT-SERVICE, CART-SERVICE, ORDER-SERVICE,               │
   │   PAYMENT-SERVICE, FULFILLMENT-SERVICE, NOTIFICATION-SERVICE  │
   └────────────────────────────────┬──────────────────────────────┘
                                    │
                                    ▼
   ┌───────────────────────────────────────────────────────────────┐
   │                    API GATEWAY (:8080)                        │
   │   /swagger-ui.html (Aggregated Docs)                          │
   │   JwtGatewayFilter (15-min JWT Auth & Downstream Headers)     │
   │   X-Gateway-Secret Injection                                  │
   └────────────────────────────────┬──────────────────────────────┘
                                    │
         ┌──────────────────────────┼──────────────────────────┐
         ▼                          ▼                          ▼
   [MEMBER 1]                 [MEMBER 2]                 [MEMBER 3]
Infrastructure & Sec       Catalog & Orders           Payments & Fulfill
- Eureka Discovery         - Vendor Registration      - Razorpay Verification
- Gateway Routing          - Admin Approval Gate      - 90/10 Vendor Ledger
- Swagger Aggregation      - Digital Catalog          - License & Download
- Perimeter (401/403)      - Cart & Order Creation    - Outbox Fault Tolerance
```

---

### Phase 1: Infrastructure, Gateway & Security (Presented by Member 1)

#### Goal:
Prove service discovery, unified gateway routing, OpenAPI aggregation, and robust zero-trust perimeter defense.

#### Step 1.1: Service Discovery in Eureka
1. Open browser to `http://localhost:8761`.
2. **Show the faculty:** All 8 active microservices are registered with status `UP (1)`:
   - `API-GATEWAY`, `IDENTITY-SERVICE`, `PRODUCT-SERVICE`, `CART-SERVICE`, `ORDER-SERVICE`, `PAYMENT-SERVICE`, `FULFILLMENT-SERVICE`, `NOTIFICATION-SERVICE`.
   - Point out that physical services (Inventory, Shipping, Warehouse) are dormant and cleanly deactivated.

#### Step 1.2: Unified Gateway Swagger UI
1. Open browser to `http://localhost:8080/swagger-ui.html`.
2. **Show the faculty:** Dropdown in the top right shows all 7 downstream service API doc specifications aggregated into a single developer portal:
   - *Select "1. Identity Service"* -> Inspect auth & user endpoints.
   - *Select "2. Product Service"* -> Inspect catalog & asset endpoints.
   - *Select "5. Payment Service"* -> Inspect verification & vendor earnings endpoints.

#### Step 1.3: Perimeter Defense & Direct Bypass Rejection
1. Open terminal and issue an unauthenticated call to a protected endpoint:
   ```powershell
   curl.exe -i http://localhost:8080/api/v1/users/me
   ```
   **Expected Response:** `HTTP/1.1 401 Unauthorized` (Token missing).
2. Attempt to bypass the Gateway and call downstream `identity-service` directly on port `8081`:
   ```powershell
   curl.exe -i http://localhost:8081/api/v1/users/me
   ```
   **Expected Response:** `HTTP/1.1 403 Forbidden` (`DownstreamSecurityFilter`: Missing valid `X-Gateway-Secret`).
3. **Faculty Talking Point:** "The API Gateway is our single point of entry. Downstream services will never accept traffic unless it originates from the gateway with our shared secret."

---

### Phase 2: Vendor Onboarding, Catalog & Orders (Presented by Member 2)

#### Goal:
Prove vendor lifecycle gating, catalog browsing, and digital order creation.

#### Step 2.1: Vendor Signup & Write-Gate Protection
1. In Swagger UI or Terminal, register a new vendor:
   ```powershell
   $reg = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/register" -Method Post -Body (@{
       email = "demo_vendor@bytevault.com"; password = "VendorPass123!"; fullName = "Demo Studio"; role = "VENDOR"
   } | ConvertTo-Json) -ContentType "application/json"
   $vToken = $reg.data.token
   ```
2. Attempt to publish a product immediately using `$vToken`:
   ```powershell
   curl.exe -i -X POST http://localhost:8080/api/v1/products/vendor?publish=true `
     -H "Authorization: Bearer $vToken" `
     -H "Content-Type: application/json" `
     -d '{"name":"Unapproved Guide","price":20.00,"productType":"DIGITAL","categoryId":1}'
   ```
   **Expected Response:** `HTTP/1.1 403 Forbidden` (Vendor status is `PENDING`).
3. Login as Admin (`admin@bytevault.com` / `AdminPass123!`), approve the vendor profile:
   ```powershell
   # Admin approves vendor application via /api/v1/admin/vendors/{id}/approve
   ```
4. Retry publishing the product:
   **Expected Response:** `HTTP/1.1 201 Created` with product status `PUBLISHED`.

#### Step 2.2: Customer Cart & Digital Order Creation
1. Authenticate as demo customer (`customer@bytevault.com` / `CustomerPass123!`):
   ```powershell
   $cLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body (@{
       email = "customer@bytevault.com"; password = "CustomerPass123!"
   } | ConvertTo-Json) -ContentType "application/json"
   $cToken = $cLogin.data.token
   ```
2. Add a published digital product to cart and checkout:
   ```powershell
   $order = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orders" -Method Post -Headers @{ Authorization = "Bearer $cToken" } -Body (@{
       items = ,@{ productId = "bcea9a84-25ce-4e46-a195-db7193a5ffb4"; quantity = 1 }
       customerName = "Demo Customer"
       customerEmail = "customer@bytevault.com"
   } | ConvertTo-Json) -ContentType "application/json"
   ```
   **Expected Response:** `HTTP 200 OK`, Order created in `PENDING` status with total amount ₹29.99.
3. **Faculty Talking Point:** "Our catalog is 100% digital. Any attempt to order a physical item is immediately rejected at the contract level."

---

### Phase 3: Payments, Fulfillment & Fault Tolerance (Presented by Member 3)

#### Goal:
Prove payment verification, mathematical 90/10 royalty accounting, license grant, signed file streaming, and outbox failure recovery.

#### Step 3.1: Payment Verification & Vendor Royalty Ledger
1. Initiate payment order and verify via `payment-service`:
   ```powershell
   # Calls POST /api/v1/payments/verify with dbOrderId and razorpayPaymentId
   ```
   **Show in Terminal / Database:**
   - Order status in `order-service` transitions to `PAID`.
   - `vendor_ledger` in `payment_db` records exact 90/10 split:
     - Gross Amount: ₹29.99
     - Platform Fee (10%): ₹3.00
     - Net Vendor Royalty (90%): ₹26.99
2. Query Vendor Earnings endpoint (`GET /api/v1/payments/vendor/earnings`):
   ```powershell
   Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/vendor/earnings" -Headers @{ Authorization = "Bearer $vToken" }
   ```
   **Expected Response:** `netEarnings = 26.99, totalOrdersCount = 1`.

#### Step 3.2: Digital Entitlements & Secure Pre-Signed Download
1. Query customer entitlements (`GET /api/v1/entitlements`):
   - Shows active license key (e.g. `BV-9142-1740`).
2. Request secure download (`GET /api/v1/downloads/{productId}`):
   - Generates HMAC-SHA256 signed URL:
     `http://localhost:8080/api/v1/products/assets/download?key=assets/...&expires=...&signature=...`
3. Download the actual binary asset using `curl`:
   ```powershell
   curl.exe -i "$signedUrl" -o downloaded_file.pdf
   ```
   **Show in File Explorer:** Real readable PDF file downloaded.
4. Tamper with the signature or expiry parameter and re-run:
   **Expected Response:** `HTTP 403 Forbidden` (Tamper-proof HMAC verification).

#### Step 3.3: Fault Tolerance & Outbox Retry Under Service Crash (The "WOW" Factor)
1. Run the empirical outbox forced-failure script:
   ```powershell
   powershell -ExecutionPolicy Bypass -File verify_7b_outbox_retry.ps1
   ```
2. **Explain to the faculty as the script runs live:**
   - Step 1: Kills `fulfillment-service` (Port 8088 closed).
   - Step 2: Creates order and marks it `PAID`.
   - Step 3: Synchronous call to Fulfillment fails with `Connection refused`.
   - Step 4: Shows the event in `order_db.outbox_events` saved as `PENDING` (no transaction loss!).
   - Step 5: Restarts `fulfillment-service`.
   - Step 6: `OutboxPublisherService` background thread automatically retries and succeeds!
   - Step 7: Shows outbox event moved to `PUBLISHED` and entitlement granted in `fulfillment_db`.
3. **Faculty Talking Point:** "Even if downstream fulfillment suffers total outage, financial transactions complete safely, and our background outbox guarantees eventual fulfillment with zero lost data."
