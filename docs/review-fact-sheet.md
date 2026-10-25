# ByteVault Media — Comprehensive Review Fact Sheet & Defense Manual (Review 2)

**Keep this handy during reviewer questioning. Every metric, configuration value, and architectural statement below is backed by empirical execution evidence on branch `review2-final`.**

---

## 1. Authoritative Core Metrics & Verification Numbers

| Metric | Authoritative Value | Empirical Source / Evidence |
| :--- | :--- | :--- |
| **Total Reactor Tests** | **295 Tests** (0 Failures, 0 Errors, 0 Skipped) | Surefire XML reports from fresh `mvn clean test` |
| **Active Digital Tests** | **242 Tests** across 8 active services + common-library | Surefire XML reports (identity 17, product 57, cart 22, order 48, payment 52, fulfillment 14, notif 6, gateway 11, common 14, eureka 1) |
| **Dormant Module Tests** | **53 Tests** (inventory 26, shipping 17, support 6, warehouse 3, config-server 1) | Surefire XML reports (kept in reactor for compile safety) |
| **Active Microservices** | **8 Active Services + 1 Eureka Discovery Server** | Eureka registry at `http://localhost:8761/eureka/apps` |
| **Databases** | **6 Dedicated PostgreSQL Databases** | `identity_db`, `product_db`, `cart_db`, `order_db`, `payment_db`, `fulfillment_db` |
| **Cart Persistence** | PostgreSQL relational tables (`carts`, `cart_items`) | `cart_db` schema (zero Redis daemon dependency) |
| **Message Brokers** | **0 Brokers** on active digital path | RabbitMQ removed; replaced by OpenFeign + PostgreSQL Transactional Outbox |
| **Marketplace Royalties** | **10% Platform Fee, 90% Net Vendor Royalty** | `payment_db.vendor_ledger` verified via `verify_priority11_live.ps1` |
| **Access Token Lifetime** | **15 Minutes** (`900000 ms`, RFC 7519 HMAC-SHA256 JWT) | `app.jwt.expiration=900000` in `identity-service` & `api-gateway` |
| **Download Link Lifetime**| **5 Minutes** (`300000 ms`, HMAC-SHA256 signature) | `app.storage.signed-url-ttl-ms=300000` in `product-service` |
| **Outbox Retry Schedule** | **Fixed 2s poll, capped backoff (2s to 30s), 20 retries (~8.5 min)** | `OutboxPublisherService.java` in `order-service` |
| **Ledger Reconciler** | **Fixed 60s rate** (`PaymentOrderSyncService.reconcileLedgerEntries`) | `payment-service` scheduled background thread |
| **Order Expiry Cron** | **Fixed 60s rate** (cancels `PENDING` orders >30 minutes) | `OrderExpiryService.java` in `order-service` |
| **Vendor Cache Window** | **60s positive TTL (`CACHE_TTL_MS`), 2s negative TTL** | `VendorAuthorizationService.java` in `product-service` |
| **Cold Start Time** | **62.8s to 76.9s** (Average: ~69.8 seconds for full 9-JVM stack) | `tools/measure-cold-start.js` |
| **RAM Footprint** | **2,395.8 MB Working Set (~2.34 GB)** across all 9 JVMs | `tools/measure-ram.js` |

---

## 2. 21-Row Old-to-New Module Mapping & Architecture Evolution

The table below traces every component from the original monolithic/bloated design to its current state on `review2-final`:

| # | Original Architecture Component | Current Review 2 Component | Status on `review2-final` | Architectural Rationale & Transformation |
| :-: | :--- | :--- | :---: | :--- |
| **1** | `discovery-server` | `discovery-server` (:8761) | **ACTIVE** | Netflix Eureka Service Registry. Heartbeat 30s, eviction 60s. |
| **2** | `config-server` | `config-server` (:8888) | **DORMANT** | Removed from startup path. Config externalized to 12-factor `.env` and `application.yml`. |
| **3** | `api-gateway` | `api-gateway` (:8080) | **ACTIVE** | Spring Cloud Gateway (Netty). JWT authentication, swagger aggregation, perimeter secret injection. |
| **4** | `auth-service` | `legacy-archived/auth-service` | **ARCHIVED** | Redundant auth logic consolidated into unified `identity-service`. |
| **5** | `user-service` | `legacy-archived/user-service` | **ARCHIVED** | User profile logic consolidated into unified `identity-service`. |
| **6** | `identity-service` | `identity-service` (:8081) | **ACTIVE** | Unified authentication, RBAC, vendor onboarding and admin approval workflow (`identity_db`). |
| **7** | `product-service` | `product-service` (:8084, :8085) | **ACTIVE** | Digital asset catalog, vendor write-gate, HMAC-SHA256 download signature streamer (`product_db`). |
| **8** | `category-service` | `product-service` (internal package) | **CONSOLIDATED** | Categories merged into `product-service` domain models to avoid unnecessary RPC hops. |
| **9** | `cart-service` | `cart-service` (:8086) | **ACTIVE** | Shopping cart persistence migrated from volatile Redis to robust PostgreSQL tables (`cart_db`). |
| **10**| `order-service` | `order-service` (:8087) | **ACTIVE** | Order state machine, transactional outbox table, stale order expiry scheduler (`order_db`). |
| **11**| `payment-service` | `payment-service` (:8091) | **ACTIVE** | Razorpay HMAC verification, 90/10 vendor royalty ledger, scheduled reconciliation (`payment_db`). |
| **12**| `fulfillment-service`| `fulfillment-service` (:8088) | **ACTIVE** | Digital license issuance, entitlement tracking, binary file streaming (`fulfillment_db`). |
| **13**| `download-service` | `fulfillment-service` (internal) | **CONSOLIDATED** | Pre-signed download generation unified inside fulfillment bounded context. |
| **14**| `notification-service`| `notification-service` (:8090)| **ACTIVE** | Transactional email dispatcher. Defensive Feign integration with 3s timeout. |
| **15**| `inventory-service` | `inventory-service` (:8082) | **DORMANT** | Physical stock management. Deactivated for Review 2 digital marketplace scope. |
| **16**| `shipping-service` | `shipping-service` (:8083) | **DORMANT** | Physical courier tracking. Deactivated for Review 2 digital marketplace scope. |
| **17**| `warehouse-service`| `warehouse-service` (:8085) | **DORMANT** | Physical bin management. Deactivated for Review 2 digital marketplace scope. |
| **18**| `support-service` | `support-service` (:8089) | **DORMANT** | Helpdesk ticketing. Excluded from core digital commerce path. |
| **19**| `common-library` | `common-library` (JAR) | **ACTIVE** | Shared DTOs, Feign security interceptors, GlobalExceptionHandler, MDC logging. |
| **20**| `frontend` | `frontend/customer-portal` | **ACTIVE** | React 18 + Vite SPA on `:5173`. Product browsing, cart checkout, digital download links. |
| **21**| `database-scripts` | `docs/database/` + Liquibase | **CONSOLIDATED** | PostgreSQL database initialization scripts unified per microservice schema. |

---

## 3. Fact Sheet per Active Microservice

### 3.1 API Gateway (`api-gateway`)
- **Port:** `8080` (HTTP) / `8443` (HTTPS with PKCS12 keystore)
- **Runtime:** Spring Cloud Gateway (Spring WebFlux / Netty Reactive)
- **Key Responsibilities:**
  1. Validates incoming JWT access tokens in `Authorization: Bearer <token>` via `JwtGatewayFilter`.
  2. Strips untrusted external identity claims (`X-User-Id`, `X-User-Roles`).
  3. Injects trusted downstream perimeter secret (`X-Gateway-Secret`) and correlation ID (`X-Correlation-ID`).
  4. Aggregates OpenAPI Swagger UI documentation across all active services.
  5. Implements in-memory sliding-window rate limiting (configurable via `app.rate-limit.requests-per-minute`).
- **Dependencies:** `discovery-server`, `common-library`.

### 3.2 Identity Service (`identity-service`)
- **Port:** `8081` | **Database:** `identity_db` (PostgreSQL)
- **Key Responsibilities:**
  1. User registration, authentication, and password reset (`/api/v1/auth/**`).
  2. Token issuance: 15-minute stateless JWT access tokens + opaque refresh tokens in `refresh_tokens`.
  3. Refresh token rotation with family revocation on replay.
  4. Vendor profile registration and admin approval gate (`/api/v1/admin/vendors/**`).
  5. Dispatches password reset emails to `notification-service`.
- **Dependencies:** `discovery-server`, `notification-service`, `common-library`.

### 3.3 Product Service (`product-service`)
- **Port:** `8084` (Primary) & `8085` (Secondary Load-Balanced Instance) | **Database:** `product_db`
- **Key Responsibilities:**
  1. Digital product catalog, categories, and pricing (`/api/v1/products/**`).
  2. Enforces digital-only marketplace policy (`app.physical-products.enabled=false`).
  3. Vendor write authorization gate with 60s positive cache and 2s negative cache (`VendorAuthorizationService`).
  4. Local asset storage fallback with HMAC-SHA256 signature verification and 5-minute URL expiry.
- **Dependencies:** `discovery-server`, `identity-service`, `common-library`.

### 3.4 Cart Service (`cart-service`)
- **Port:** `8086` | **Database:** `cart_db` (PostgreSQL)
- **Key Responsibilities:**
  1. Relational shopping cart persistence in tables `carts` and `cart_items`.
  2. Cart management per authenticated user (`X-User-Id`).
  3. Price and item validation before checkout handoff.
- **Dependencies:** `discovery-server`, `product-service`, `common-library`.

### 3.5 Order Service (`order-service`)
- **Port:** `8087` | **Database:** `order_db` (PostgreSQL)
- **Key Responsibilities:**
  1. Order creation and authoritative state machine (`PENDING` -> `PAID` -> `CANCELLED` / `REFUNDED`).
  2. Transactional outbox event insertion (`order_db.outbox_events`) in the same database transaction as `markPaid`.
  3. Scheduled `OutboxPublisherService` (runs every 2s, 20 retry attempts with backoff) calling `fulfillment-service`.
  4. Dispatches order confirmation email defensively to `notification-service`.
  5. Scheduled `OrderExpiryService` (runs every 60s) auto-cancelling stale `PENDING` orders >30 minutes old.
- **Dependencies:** `discovery-server`, `fulfillment-service`, `notification-service`, `common-library`.

### 3.6 Payment Service (`payment-service`)
- **Port:** `8091` | **Database:** `payment_db` (PostgreSQL)
- **Key Responsibilities:**
  1. Razorpay payment order initialization and HMAC-SHA256 signature verification (`/api/v1/payments/**`).
  2. Multi-vendor 90/10 royalty ledger accounting in `vendor_ledger` table.
  3. Idempotent replay handling for duplicate payment verification webhooks.
  4. Synchronous handoff to `order-service` via Feign client (`/internal/{id}/mark-paid`).
  5. Scheduled reconciler `PaymentOrderSyncService.reconcileLedgerEntries()` (runs every 60s) backfilling missed ledger rows.
  6. Full and partial refund processing with proportional ledger reversals.
- **Dependencies:** `discovery-server`, `order-service`, `common-library`.

### 3.7 Fulfillment Service (`fulfillment-service`)
- **Port:** `8088` | **Database:** `fulfillment_db` (PostgreSQL)
- **Key Responsibilities:**
  1. Digital entitlement license issuance upon order completion (`entitlements` table).
  2. Double fulfillment idempotency guard (prevents duplicate entitlements).
  3. Generates HMAC-SHA256 pre-signed download tokens (`key + ":" + expires`) valid for 5 minutes.
  4. Streams binary files (valid PDF / ZIP assets) directly to authorized users.
  5. Revokes entitlements upon full refund, immediately blocking download streaming.
- **Dependencies:** `discovery-server`, `product-service`, `common-library`.

### 3.8 Notification Service (`notification-service`)
- **Port:** `8090` | **Database:** None (Stateless)
- **Key Responsibilities:**
  1. Consumes transactional email dispatch requests from `identity-service` and `order-service`.
  2. Non-blocking defensive processing with 3s timeouts; email dispatch failure never aborts order completion.
- **Dependencies:** `discovery-server`, `common-library`.

---

## 4. Defensible Answers to Key Reviewer Questions

### Q1: Why did you remove RabbitMQ and Redis instead of keeping them?
> **Answer:** Prior iterations suffered from operational bloat and demo fragility. In a distributed digital marketplace with write volumes under 5,000 req/min, running external message broker and caching daemons introduced cross-node connection failures, message loss on ungraceful consumer exits, and dual-write split-brain bugs. By migrating Cart persistence to PostgreSQL and adopting the **Transactional Outbox Pattern** in `order_db`, we achieved 100% ACID consistency, deterministic outbox retries (empirically proven up to 20 attempts), and zero operational daemon overhead.

### Q2: How does your system guarantee that an order is fulfilled if the Fulfillment Service is crashed?
> **Answer:** When payment verification completes, `OrderService.markOrderPaid()` updates the order to `PAID` and inserts an event into `order_db.outbox_events` in the **same atomic database transaction**. The background `OutboxPublisherService` polls every 2 seconds for `PENDING` events. If `fulfillment-service` is down, the call fails gracefully, incrementing `retry_count` with exponential backoff up to 20 attempts (~8.5 minutes window). As demonstrated in our live forced failure test (`verify_7b_outbox_retry.ps1`), when `fulfillment-service` restarts, the scheduled publisher automatically delivers the pending event, transitioning it to `PUBLISHED` and granting the user's digital entitlement.

### Q3: Why is the access token lifetime 15 minutes, and how does logout work?
> **Answer:** In compliance with RFC 7519 and industry best practices for stateless JWTs, access tokens are short-lived (15 minutes / 900,000 ms) and cannot be modified once signed by `app.jwt.secret`. Upon logout, the client's refresh token is revoked in `identity_db.refresh_tokens`, preventing renewal. The stateless access token expires naturally within its remaining 15-minute lifespan. This avoids maintaining an unverified in-memory blacklist while keeping the attack window small.

### Q4: How does your architecture prevent IDOR (Insecure Direct Object Reference) vulnerabilities?
> **Answer:** IDOR is prevented at three distinct layers:
> 1. **Perimeter Strip:** `JwtGatewayFilter` strips any externally supplied `X-User-Id` or `X-User-Roles` headers before routing downstream.
> 2. **Trusted Injection:** The Gateway validates the cryptographically signed JWT and injects verified `X-User-Id` and `X-User-Roles` headers alongside `X-Gateway-Secret`.
> 3. **Controller-Level Tenancy Checks:** Downstream endpoints compare the resource owner ID against the caller's verified `X-User-Id`. For example, `VendorLedgerService` rejects any vendor query for another vendor's earnings with HTTP 403 Forbidden (`verify_priority11_live.ps1` [A6]).

### Q5: How do you handle load balancing and high availability for the digital catalog?
> **Answer:** We run dual instances of `product-service` on ports 8084 and 8085, both registered with Eureka as `UP`. Spring Cloud Gateway resolves routes via `lb://product-service` using `RoundRobinLoadBalancer`. In our empirical benchmark (`docs/evidence/section2-deep-dive-*.log`), 100 requests through the gateway achieved 100% success rate with a median latency of 20 ms and maximum latency of 301 ms, cleanly distributed across both instances.
