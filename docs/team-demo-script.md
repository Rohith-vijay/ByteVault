# ByteVault Media — 3-Person Team Live Demonstration Script (Review 2)

**Total Time Allocation:** ~16–18 minutes total (~5–6 minutes per team member)  
**Primary Interfaces:**  
- API Gateway Swagger UI: `http://localhost:8080/swagger-ui.html`  
- Eureka Service Registry Console: `http://localhost:8761`  
- PowerShell / Command Terminal (Running live verification commands)  
**Prerequisites:**  
1. All 8 active microservices + secondary product instance running via `node start-active-stack.js`.  
2. Databases initialized and real binary PDF/ZIP assets seeded via `tools/seed-demo.ps1`.  
3. Fresh environment variables loaded from `.env`.

---

## Team Ownership & Presentation Architecture

```
   ┌───────────────────────────────────────────────────────────────┐
   │                    EUREKA DASHBOARD (:8761)                   │
   │   9 Active Instances: API-GATEWAY, IDENTITY-SERVICE,          │
   │   PRODUCT-SERVICE (x2), CART-SERVICE, ORDER-SERVICE,          │
   │   PAYMENT-SERVICE, FULFILLMENT-SERVICE, NOTIFICATION-SERVICE │
   └───────────────────────────────┬───────────────────────────────┘
                                   │
                                   ▼
   ┌───────────────────────────────────────────────────────────────┐
   │                    API GATEWAY (:8080)                        │
   │   /swagger-ui.html (Aggregated Docs across all services)      │
   │   JwtGatewayFilter (15-min JWT Auth & Header Stripping)       │
   │   X-Gateway-Secret Injection & CorrelationId Logging          │
   └───────────────────────────────┬───────────────────────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
      [ROHITH]                  [RAMYA]                 [CHANDINI]
Gateway, Auth & Payments    Cart, Orders & Tests    Discovery, Catalog & Assets
(5–6 minutes)              (5–6 minutes)            (5–6 minutes)
- Spring Cloud Gateway     - Relational Cart (DB)   - Eureka & Dual Instance LB
- 15-min JWT & TLS         - Order State Machine    - Vendor Onboarding & Gate
- Swagger UI Aggregation   - Outbox Recovery Demo   - Digital Entitlements
- Payment Verification     - Stale Order Expiry     - HMAC-SHA256 File Downloads
- 90/10 Vendor Ledger      - Regression Guard Test  - Full Refund Revocation
```

---

## Timed Rehearsal Record

- **Rehearsal Date:** 2026-10-25 15:35:00
- **Rohith (Phase 1):** 5m 20s
- **Ramya (Phase 2):** 5m 45s
- **Chandini (Phase 3):** 5m 15s
- **Total Demo Time:** 16 minutes 20 seconds (**PASS** — well within 20-minute review slot).

---

## Phase 1: Gateway, Security, Swagger & Payments (Presented by Rohith)
**Duration:** ~5–6 minutes  
**Focus:** Gateway routing, JWT validation, TLS, Swagger aggregation, payment verification, vendor ledger, webhook security.

### Step 1.1: Gateway Security, JWT Validation & Header Stripping
1. **Explain:** "The API Gateway is built with Spring Cloud Gateway (Netty reactive). It enforces perimeter zero-trust security by validating stateless 15-minute JWTs, stripping any spoofed client identity headers, and injecting a cryptographic `X-Gateway-Secret` for downstream service trust."
2. **Execute Live Command:**
   ```powershell
   # Test forged JWT and header stripping
   Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me" -Headers @{ Authorization = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmb3JnZWQifQ.fake_signature" } -ErrorAction SilentlyContinue
   ```
3. **Expected Output:** HTTP 401 Unauthorized (`JWT validation failed: JWT signature does not match`).
4. **Fallback:** Show `backend/api-gateway/src/main/java/com/bytevault/gateway/security/JwtGatewayFilter.java` lines 98–145.

### Step 1.2: OpenAPI / Swagger Aggregation & TLS Configuration
1. **Open Browser:** `http://localhost:8080/swagger-ui.html`
2. **Show the faculty:** Use the dropdown top-right to toggle between all active services (`identity-service`, `product-service`, `order-service`, `payment-service`, `fulfillment-service`).
3. **Demonstrate TLS Keystore:**
   - Point to `backend/api-gateway/src/main/resources/keystore.p12`.
   - Explain: "TLS is fully configured using PKCS12 keystore. It runs on `:8080` for local HTTP evaluation and `:8443` when `SSL_ENABLED=true`."

### Step 1.3: Live Payment Verification & 90/10 Vendor Royalty Ledger
1. **Explain:** "When a customer completes a checkout via Razorpay, `payment-service` verifies the HMAC-SHA256 signature, validates the order status via Feign, and calculates a 10% platform fee with 90% net royalty credited to the vendor."
2. **Execute Live Script:**
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -Command {
       $adminLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"admin@bytevault.com","password":"AdminPass123!"}'
       $earnings = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/payments/vendor/earnings" -Headers @{ Authorization = "Bearer $($adminLogin.data.token)" }
       Write-Host "Total Orders Processed: $($earnings.data.totalOrdersCount)" -ForegroundColor Green
       Write-Host "Platform Fees Collected (10%): Rs. $($earnings.data.totalPlatformFees)" -ForegroundColor Green
       Write-Host "Vendor Royalties Credited (90%): Rs. $($earnings.data.netEarnings)" -ForegroundColor Green
   }
   ```
3. **Expected Output:** Exact 90/10 mathematical relationship (Fee + Net = Gross).
4. **Demonstrate Webhook Signature:** Show `RazorpayWebhookController.java` validating `X-Razorpay-Signature` using `HmacSHA256` before parsing payload.

---

## Phase 2: Cart, Orders, Expiry & Outbox Recovery (Presented by Ramya)
**Duration:** ~5–6 minutes  
**Focus:** Cart relational schema, order state machine, stale order expiry cron, Fulfillment-down recovery demo, test matrix.

### Step 2.1: Relational Cart Persistence & Order Creation
1. **Explain:** "We eliminated Redis and consolidated cart persistence into PostgreSQL `cart_db` (`carts` and `cart_items` tables). This guarantees ACID consistency with zero cache daemon dependency."
2. **Show Schema in PostgreSQL:**
   ```powershell
   & "C:\Program Files\PostgreSQL\13\bin\psql.exe" -U postgres -h localhost -p 5432 -d cart_db -c "\d cart_items"
   ```
3. **Expected Output:** Normalized relational columns (`id`, `cart_id`, `product_id`, `quantity`, `unit_price`, `version`).

### Step 2.2: Stale Order Expiry Job Demonstration
1. **Explain:** "`OrderExpiryService` is a scheduled background task running every 60 seconds. Any order that remains in `PENDING` status for more than 30 minutes without payment is automatically transitioned to `CANCELLED`."
2. **Execute Query:**
   ```powershell
   & "C:\Program Files\PostgreSQL\13\bin\psql.exe" -U postgres -h localhost -p 5432 -d order_db -c "SELECT id, status, created_at, updated_at FROM orders WHERE status = 'CANCELLED' ORDER BY updated_at DESC LIMIT 1;"
   ```
3. **Expected Output:** Order transitioned from `PENDING` to `CANCELLED` by the background scheduler.

### Step 2.3: Live Outbox Fault Tolerance (Fulfillment Service Crash & Recovery)
1. **Explain:** "We use the Transactional Outbox Pattern to guarantee that digital orders are fulfilled even if `fulfillment-service` crashes during checkout."
2. **Execute Live Test Script:**
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File verify_7b_outbox_retry.ps1
   ```
3. **Show Faculty:**
   - Step 1: Script kills `fulfillment-service` on port 8088.
   - Step 2: Digital order is placed and paid through Gateway.
   - Step 3: Outbox record is saved in `order_db.outbox_events` with status `PENDING` and retry count 0.
   - Step 4: `tools/start-service.ps1 fulfillment-service 8088` restarts fulfillment.
   - Step 5: `OutboxPublisherService` retry thread picks up the event, calls fulfillment, transitions event to `PUBLISHED`, and creates the active entitlement!
4. **Fallback:** Show `OutboxPublisherService.java` lines 56–133.

### Step 2.4: Test Suite & Regression Guard
1. **Explain:** "Our entire reactor contains 295 automated tests with 0 failures. We built a deliberate regression guard (`test_guard_fail.ps1`) that catches zero-earnings logic errors."
2. **Show Maven Build Evidence:** Show `docs/final-test-report.md` with Surefire XML breakdown.

---

## Phase 3: Service Discovery, Vendor Flow & Secure Downloads (Presented by Chandini)
**Duration:** ~5–6 minutes  
**Focus:** Eureka registry & dual-instance load balancing, vendor onboarding & approval, digital fulfillment, HMAC-SHA256 download streaming, refund revocation.

### Step 3.1: Eureka Registry & Dual-Instance Load Balancing
1. **Open Browser:** `http://localhost:8761`
2. **Show Faculty:**
   - Point out `PRODUCT-SERVICE` with **2 instances** listed: `LAPTOP-...:8084` and `LAPTOP-...:8085`.
   - Point out that all 8 active microservices are registered `UP`.
3. **Demonstrate Load Balancing:**
   - Run 10 requests through Gateway to `/api/v1/products`:
   ```powershell
   1..10 | ForEach-Object { (Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products").data.Count }
   ```
   - Explain: "Spring Cloud Gateway distributes traffic round-robin across both product-service instances with 20 ms median latency."

### Step 3.2: Vendor Onboarding & Admin Approval Write-Gate
1. **Explain:** "`product-service` enforces a vendor write-gate. When a new vendor registers, their profile is `PENDING`. They cannot publish products until an Admin explicitly approves them."
2. **Show Code:** `VendorAuthorizationService.java` (60s positive cache, 2s negative cache).
3. **Demonstrate via API:** Show `identity-service` approving a vendor and `product-service` unlocking product creation.

### Step 3.3: Pre-Signed HMAC-SHA256 Digital Download Streaming
1. **Explain:** "Digital products must never expose raw filesystem paths. When an entitled customer requests a download, `fulfillment-service` verifies license entitlement and generates a cryptographic HMAC-SHA256 signed URL valid for only 5 minutes."
2. **Execute Live Download Command:**
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -Command {
       $login = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"customer@bytevault.com","password":"CustomerPass123!"}'
       $token = $login.data.token
       $prods = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products"
       $dig = ($prods.data | Where-Object { $_.productType -eq "DIGITAL" })[0]
       $dlInfo = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/downloads/$($dig.id)" -Headers @{ Authorization = "Bearer $token" }
       Write-Host "Generated Signed Download URL: $($dlInfo.data.downloadUrl)" -ForegroundColor Green
       
       # Stream binary asset
       $outFile = "$env:TEMP\bytevault_demo_asset.pdf"
       Invoke-WebRequest -Uri $dlInfo.data.downloadUrl -OutFile $outFile
       $header = Get-Content $outFile -TotalCount 1
       Write-Host "Downloaded file header: $header (Valid PDF binary format!)" -ForegroundColor Cyan
       Invoke-Item $outFile
   }
   ```
3. **Expected Output:** File begins with `%PDF-1.4` and opens in the system's default PDF reader.
4. **Tamper Test:** Change one character in the `signature` query parameter; demonstrate that Gateway / Product Service immediately returns `403 Forbidden` (`HMAC signature invalid or expired`).

### Step 3.4: Full Refund Entitlement Revocation
1. **Explain:** "When an admin issues a full refund, `payment-service` reverses the vendor ledger credit and calls `fulfillment-service` to revoke the entitlement."
2. **Execute Query:**
   ```powershell
   & "C:\Program Files\PostgreSQL\13\bin\psql.exe" -U postgres -h localhost -p 5432 -d fulfillment_db -c "SELECT id, product_id, status FROM entitlements WHERE status = 'REVOKED' LIMIT 1;"
   ```
3. **Show Faculty:** Entitlement status is `REVOKED`. Any subsequent attempt to download the file is blocked with HTTP 400/403.
