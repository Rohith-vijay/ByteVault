# ByteVault Media — Fast Fact Sheet (Review 2)

**Keep this handy during review questions. Every number and statement below is backed by real execution output on `review2-final`.**

---

## 1. Core Architecture Numbers

- **Active Microservices:** **8** (`identity-service`, `product-service`, `cart-service`, `order-service`, `payment-service`, `fulfillment-service`, `notification-service`, `api-gateway`).
- **Infrastructure Services:** **1** (`discovery-server` / Netflix Eureka on `:8761`).
- **Dormant Services:** **4** physical/legacy services (`inventory-service`, `shipping-service`, `warehouse-service`, `support-service`) — deactivated from Eureka and Gateway routes in this phase.
- **Message Brokers:** **0** on active digital path. RabbitMQ was deliberately replaced with synchronous OpenFeign and transactional outbox persistence for simplicity and reliability.
- **Caching Brokers:** **0** (Redis removed; Cart migrated to PostgreSQL with native JSONB/table isolation).
- **Databases:** **6** independent PostgreSQL databases (`identity_db`, `product_db`, `cart_db`, `order_db`, `payment_db`, `fulfillment_db`) — 100% database-per-service isolation.
- **Digital Catalog Policy:** **100% Digital**. `app.physical-products.enabled=false`. Physical item creation and orders are rejected at the contract level (400 Bad Request).
- **Platform Commission:** **10%** platform fee, **90%** net vendor royalty payout.

---

## 2. Testing & Verification Metrics

- **Reactor Maven Tests:** **293 Tests total** (0 failures, 0 errors, 0 skipped) across all 16 modules.
- **New Regression Tests Added in This Phase:** **11 new tests** covering webhook signature security, storage download tamper protection, physical product rejection, outbox retry windows, and vendor royalty assertions.
- **Automated Verification Suite:** **11 / 11 Tests PASS (100% success)** via `backend/postman/run-verification.ps1`.
- **Live Multi-Role Scenarios:** **6 / 6 Matrix Scenarios PASS** (A1 purchase & royalty, A2 idempotent replay, A3 reconciler backfill, A4 full & partial refund, A5 stale order expiry, A6 perimeter & anti-IDOR).
- **Outbox Resilience Under Forced Failure:** **100% PASS** via `verify_7b_outbox_retry.ps1` (fulfillment-service killed, outbox saved as `PENDING`, auto-retried to `PUBLISHED` upon service recovery).
- **Cold Start Time (Full 9-Process JVM Stack):** **62.8s to 76.9s** (Average: ~69.8 seconds).
- **RAM Footprint (All 9 JVMs Running):** **2,395.8 MB Working Set (~2.34 GB)**; Private Memory 2,798.1 MB (~2.73 GB).

---

## 3. Security Implementation Facts

- **Authentication Protocol:** Stateless RFC 7519 JWT access tokens using HMAC-SHA256 (`HS256`).
- **Token Lifetime:** Exactly **15 minutes** (`app.jwt.expiration=900000`).
- **Refresh Token Strategy:** Stored in PostgreSQL `refresh_tokens` table. Revoked on logout.
- **Perimeter Defense:** Gateway injects `X-Gateway-Secret` and trusted claims (`X-User-Id`, `X-User-Roles`).
- **Downstream Protection:** `DownstreamSecurityFilter` installed on every downstream service; direct bypass returns `403 Forbidden`.
- **Distributed Observability:** MDC `correlationId` extracted from `X-Correlation-ID` at `HIGHEST_PRECEDENCE` and logged across all service calls (`[%X{correlationId}]`).
- **Asset Download Security:** Local storage fallback generates HMAC-SHA256 signed download tokens (`key + ":" + expires`), verified at `/api/v1/products/assets/download`.

---

## 4. Quick Port & Service Directory

| Service | Port | Database | Primary Responsibility |
| :--- | :---: | :---: | :--- |
| **Discovery Server** | `8761` | None | Netflix Eureka Service Registry |
| **API Gateway** | `8080` | None | Spring Cloud Gateway, JWT Filter, Swagger Aggregation |
| **Identity Service** | `8081` | `identity_db` | Authentication, RBAC, Vendor Onboarding & Approval |
| **Product Service** | `8084` | `product_db` | Digital Catalog, Vendor Write-Gate, Signed File URLs |
| **Cart Service** | `8086` | `cart_db` | Persistent Shopping Cart in PostgreSQL |
| **Order Service** | `8087` | `order_db` | Order Lifecycle, Transactional Outbox, Expiry Cron |
| **Fulfillment Service**| `8088` | `fulfillment_db`| Digital Entitlements, Licenses, Download Streaming |
| **Notification Service**| `8090` | None | Transactional Emails (Defensive non-blocking Feign) |
| **Payment Service** | `8091` | `payment_db` | Razorpay HMAC-SHA256 Verification, 90/10 Royalty Ledger |
