# ByteVault Media — Comprehensive Rubric Compliance Matrix (Review 2 & Review 3 Basics)

**System:** ByteVault Media — Cloud-Native Digital Commerce Platform  
**Evaluation Standard:** Objective Evidence-Based Rubric Assessment  
**Audit Date:** 2026-10-25  

---

## 1. Ten-Line Rubric Evaluation Matrix

| # | Evaluation Line | Score | Evidence Source (File / Command) | What a Reviewer Could Still Probe | Smallest Improvement |
| :-: | :--- | :---: | :--- | :--- | :--- |
| **1** | **Design Refinement & Bounded Contexts** | **9.0 / 10** | `docs/architecture.md`<br/>`backend/pom.xml` (`<modules>`) | Check whether cart items duplicate product catalog data; verify if legacy physical modules compile. | Enforce strict value object mapping on cart line items so price changes in catalog do not retroactively alter checked-out orders. |
| **2** | **Service Implementation & State Machine** | **9.0 / 10** | `backend/order-service/.../OrderService.java`<br/>`verify_priority11_live.ps1` (`[A5]`) | Ask why `OrderExpiryService` is a 60s poller rather than an event-scheduled delay queue; test order concurrency. | Add database row-level locking (`SELECT ... FOR UPDATE`) on order state transitions during simultaneous webhook receipts. |
| **3** | **JWT Authentication & RBAC Security** | **8.5 / 10** | `tools/verify-matrix-gaps.ps1` (`[1.1-1.5]`)<br/>`identity_db.refresh_tokens` | Probe that access tokens remain valid until their 15-minute expiration even after user clicks logout; test token blacklisting. | Add a lightweight Redis or distributed in-memory blacklist filter for revoked JTI tokens, or reduce access token TTL to 5 minutes. |
| **4** | **API Gateway Routing & Perimeter Control** | **9.0 / 10** | `backend/api-gateway/src/main/resources/application.yml`<br/>`JwtGatewayFilter.java` | Check whether rate limiting is distributed across multiple gateway instances; check HTTPS `:8443` default state. | Integrate Redis-backed distributed rate limiter via Spring Cloud Gateway RequestRateLimiter for multi-instance clusters. |
| **5** | **Inter-Service Communication & Feign Security** | **9.0 / 10** | `backend/common-library/.../CommonFeignConfig.java`<br/>`verify_7b_outbox_retry.ps1` | Probe what happens when downstream service times out (>3s); check circuit breaker fallback handling. | Add Resilience4j circuit breaker fallback factories on all Feign client interfaces returning cached or empty responses. |
| **6** | **Data Persistence & Database-per-Service Isolation** | **9.5 / 10** | PostgreSQL databases: `identity_db`, `product_db`, `cart_db`, `order_db`, `payment_db`, `fulfillment_db` | Verify whether any foreign keys cross database boundaries; check connection pool sizing under heavy traffic. | Run automated Flyway/Liquibase migration scripts on startup instead of JPA `ddl-auto: update`. |
| **7** | **Testing & Quality Assurance Matrix** | **9.0 / 10** | `mvn clean test` (295 tests, 0 failures)<br/>`docs/evidence/test-matrix-execution-*.log` | Probe why dormant modules have 53 tests not active in demo; check frontend automated unit test coverage. | Add Playwright/Cypress end-to-end automated UI test pipeline executing alongside Maven surefire build. |
| **8** | **DevOps, Repeatability & Service Supervision** | **8.5 / 10** | `start-active-stack.js`<br/>`tools/start-service.ps1`<br/>`tools/seed-demo.ps1` | Probe how the stack runs on Linux/Docker instead of Windows PowerShell; test single container crash recovery. | Provide a production multi-stage `docker-compose.yml` with healthchecks alongside local PowerShell/Node tooling. |
| **9** | **Fault Tolerance & Reconciler Healing** | **9.0 / 10** | `verify_priority11_live.ps1` (`[A3, A4]`)<br/>`PaymentOrderSyncService.java` | Check outbox queue behavior when downstream outage exceeds the 20-retry window (~8.5 min); check dead-letter table. | Implement an automated dead-letter queue (DLQ) table and admin dashboard to trigger manual replay with one click. |
| **10** | **API Documentation & OpenAPI Contracts** | **9.5 / 10** | `http://localhost:8080/swagger-ui.html`<br/>`tools/verify-swagger.ps1` | Check whether OpenAPI schemas document error responses (400, 401, 403, 404, 500) on all endpoints. | Add `@ApiResponses` annotations with schema definitions for all exception payloads across all controllers. |

---

## 2. Platform Architecture Limitations Table

The following table documents all architectural boundaries, deliberate simplifications, and known constraints of ByteVault Media in Review 2:

| # | Domain / Subsystem | Current Implementation / Constraint | Reviewer Impact / Explanation | Production Path Forward |
| :-: | :--- | :--- | :--- | :--- |
| **1** | **OAuth Authentication** | Disabled by default (`app.oauth.enabled=false`). Endpoint `/api/v1/auth/oauth` rejects unverified requests with HTTP 400. | Protects system from forged third-party tokens in demo. Prevents unverified ID token session injection. | Integrate Google/GitHub OAuth2 IdP cryptographic verification via Spring Security OAuth2 Client. |
| **2** | **Stateless Logout Semantics** | Access tokens are stateless RFC 7519 HMAC-SHA256 JWTs with a 15-minute TTL (`900000 ms`). Logout revokes the refresh token in `identity_db`. | Access token remains valid for remaining minutes of its 15-min lifespan if intercepted before logout. | Implement distributed token blacklist (Redis SETEX with token JTI and remaining TTL). |
| **3** | **Default Demo Secrets** | Standard default credentials in `.env.example` (`platform_default_gateway_shared_secret`, demo passwords). | Works out of the box for grading without complex vault setup; overridable via `.env`. | Inject dynamic secrets from HashiCorp Vault or AWS Secrets Manager via environment variables. |
| **4** | **Per-Instance Rate Limiting** | Gateway uses in-memory sliding-window counter (`ConcurrentHashMap` in `JwtGatewayFilter`). | Rate limits apply per gateway instance, not globally shared across multiple gateway replicas. | Use Redis-backed `RequestRateLimiter` with leaky-bucket algorithm via Lettuce connection pool. |
| **5** | **Sandbox Payment Simulation** | Razorpay sandbox simulator mode active by default (`app.razorpay.simulation=true`). | Allows full end-to-end purchasing and verification flows without requiring live bank/credit card test keys. | Set `app.razorpay.key-id` and `key-secret` with production webhook HMAC secret; simulator auto-disables. |
| **6** | **Transactional Outbox Window** | 20 retry attempts with exponential backoff up to 30s cap (~8.5 minutes total recovery window). | Outages of `fulfillment-service` lasting <8.5 minutes recover automatically. Outages >8.5 min transition event to `FAILED`. | Configure configurable retry cap (e.g. 5 minutes cap covering >2 hours) and admin manual retry endpoint. |
| **7** | **Vendor Approval Cache TTL** | Positive cache TTL is 60 seconds (`CACHE_TTL_MS`); negative cache TTL is 2 seconds. | When a vendor is suspended, catalog continues serving cached status for up to 60s before products are hidden. | Implement cache invalidation webhook or direct cache eviction call upon admin vendor status change. |
| **8** | **Physical Goods Deferral** | Physical modules (`inventory-service`, `shipping-service`, `warehouse-service`) compile but remain dormant. | System strictly enforces `productType == DIGITAL`. Eliminates half-baked warehouse/courier mocks. | Review 3: Activate warehouse bin allocation and courier API integrations for physical merchandise. |
| **9** | **Observability & Dashboards** | Logback MDC correlation ID propagation across all services; centralized actuator health. | Distributed tracing (Zipkin/Jaeger) and Prometheus/Grafana dashboards are not deployed locally. | Deploy OpenTelemetry Java agent exporting traces and metrics to Grafana/Prometheus stack. |
| **10** | **Frontend Token Refresh** | Frontend `apiClient.js` lacks an automatic 401 response interceptor for silent background refresh. | If user stays idle >15 minutes, access token expires and subsequent API calls return HTTP 401. | Add Axios response interceptor invoking `authService.refresh(refreshToken)` and retrying original request. |
