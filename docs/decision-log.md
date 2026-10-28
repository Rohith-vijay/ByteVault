# ByteVault Media — Architectural Decision Log (ADR)

This document formalizes the engineering decisions, tradeoffs, and architectural evolutions made for the ByteVault Media cloud-native digital commerce platform leading up to Review 2.

---

## ADR 01: Cart Persistence Migration from Redis to PostgreSQL

- **Date:** 2026-10-23
- **Status:** **ACCEPTED & IMPLEMENTED**
- **Context:**
  The initial architecture specified Redis for shopping cart storage. However, running a Redis server introduced an external non-relational dependency, cross-service connection overhead, lack of transactional consistency with order creation, and cache volatility in single-node demo environments.
- **Decision:**
  Migrate `cart-service` persistence to dedicated PostgreSQL relational schema (`cart_db`) using standard tables `carts` and `cart_items` with foreign keys, optimistic locking (`version`), and indexed lookups on `user_id`.
- **Trade-offs & Rationale:**
  - *Positive:* ACID compliance, identical persistence model across all services, zero Redis daemon dependency, persistent cart recovery across crashes/reboots.
  - *Negative:* Slightly higher write latency compared to pure in-memory Redis, acceptable given ByteVault's low-to-medium write throughput (<5,000 req/min).

---

## ADR 02: Elimination of RabbitMQ in Favor of OpenFeign + Transactional Outbox

- **Date:** 2026-10-23
- **Status:** **ACCEPTED & IMPLEMENTED**
- **Context:**
  Previous iterations utilized RabbitMQ (AMQP) for asynchronous event publishing between Order, Payment, and Fulfillment. In practice, RabbitMQ introduced broker connection failures, message loss when consumers crashed, complex mock setups for integration testing, and operational fragility.
- **Decision:**
  Remove RabbitMQ and `spring-boot-starter-amqp`. Standardize on synchronous OpenFeign REST communication for immediate inter-service commands, backed by the **Transactional Outbox Pattern** (`order_db.outbox_events`). Outbox events are written in the same local database transaction as order status updates and processed by a scheduled poller (`OutboxPublisherService`, 2-second interval, capped backoff).
- **Trade-offs & Rationale:**
  - *Positive:* Zero broker operational overhead; guaranteed local ACID atomicity; deterministic replay and empirical failure recovery; outbox events survive service and container restarts.
  - *Negative:* Polling database for outbox introduces small query overhead every 2 seconds, negligible with proper indexing on `(status, next_retry_at)`.

---

## ADR 03: Removal of Standalone Spring Cloud Config Server

- **Date:** 2026-10-24
- **Status:** **ACCEPTED & IMPLEMENTED**
- **Context:**
  The repository initially included a `config-server` module intended to fetch configuration from a Git repository or local filesystem. In local testing, services failed to boot when config server was delayed, circular startup dependencies occurred, and configuration changes required two commits.
- **Decision:**
  Decommission `config-server` from the active startup path (archived in reactor as dormant). Standardize configuration via Spring 12-factor application properties (`application.yml`) with environment variable overrides loaded from root `.env`.
- **Trade-offs & Rationale:**
  - *Positive:* Completely independent service cold starts; immediate configuration changes via `.env`; deterministic boot sequence; zero single-point-of-failure on config server.
  - *Negative:* Centralized runtime dynamic property refreshes without restart are not available, which is unnecessary for Review 2 evaluation.

---

## ADR 04: Retention of OpenFeign for Inter-Service Communication

- **Date:** 2026-10-24
- **Status:** **ACCEPTED & IMPLEMENTED**
- **Context:**
  Evaluated replacing Spring Cloud OpenFeign with raw `WebClient` or `RestTemplate`.
- **Decision:**
  Retain OpenFeign across all active microservices (`order-service`, `payment-service`, `fulfillment-service`, `identity-service`, `product-service`). Centralize security interception in `common-library` (`CommonFeignConfig` / `FeignGatewaySecretInterceptor`) to automatically inject `X-Gateway-Secret` and propagate correlation IDs across internal HTTP calls.
- **Trade-offs & Rationale:**
  - *Positive:* Clean declarative interface definitions; built-in Jackson serialization; automatic Eureka service discovery load balancing; transparent security header propagation.
  - *Negative:* Synchronous thread-per-request blocking inside Spring MVC, mitigated by JVM thread pooling and strict timeout configurations (3s connect / 5s read).

---

## ADR 05: Evaluation of Apache Kafka vs Transactional Outbox (Compared, Not Built)

- **Date:** 2026-10-24
- **Status:** **EVALUATED & REJECTED FOR REVIEW 2**
- **Context:**
  Evaluated introducing Apache Kafka for event-driven publish/subscribe event distribution across the marketplace.
- **Comparison & Decision:**
  | Dimension | Apache Kafka | PostgreSQL Transactional Outbox |
  | :--- | :--- | :--- |
  | **Operational Footprint** | Heavy (Zookeeper/KRaft, 1.5GB+ JVM RAM) | Zero additional (shares existing PostgreSQL) |
  | **Two-Phase Commit** | Requires dual-write distributed coordination | Single local ACID transaction |
  | **Message Delivery** | At-least-once with consumer offsets | At-least-once within 20 retry window |
  | **Auditability** | Topic inspection tooling required | Standard SQL queries on `outbox_events` |
  | **Target Scale** | >50,000 events/sec | <10,000 events/min |
  - *Conclusion:* Kafka is over-engineered for the current capstone scale. The PostgreSQL Transactional Outbox provides identical at-least-once delivery semantics with zero broker management overhead.

---

## ADR 06: Deferral of Physical Fulfillment (Inventory, Shipping, Warehouse)

- **Date:** 2026-10-24
- **Status:** **ACCEPTED & ENFORCED**
- **Context:**
  The system historically contained placeholder modules for physical goods: `inventory-service`, `shipping-service`, and `warehouse-service`. Physical fulfillment requires complex courier APIs, tracking number webhooks, physical warehouse bin management, and returns logistics that were incomplete.
- **Decision:**
  Formally enforce digital-only policy for Review 2 (`app.physical-products.enabled=false`). Product creation validates `productType == DIGITAL` and rejects physical goods. Modules `inventory-service`, `shipping-service`, and `warehouse-service` remain in the reactor for compilation verification but are kept dormant during demo runtime.
- **Trade-offs & Rationale:**
  - *Positive:* 100% stable, fully automated end-to-end user journey (Add to Cart -> Order -> Razorpay Pay -> Outbox -> Instant License Entitlement -> HMAC Download Stream); zero half-baked or mocked shipping flows.
  - *Negative:* Physical marketplace goods deferred to Review 3 / future scope.

---

## ADR 07: Exclusion of Support Service from Active Stack

- **Date:** 2026-10-24
- **Status:** **ACCEPTED & EXCLUDED**
- **Context:**
  `support-service` provided basic CRUD ticketing endpoints without real-time websocket chat or integration with the active marketplace order flow.
- **Decision:**
  Exclude `support-service` from active stack startup. The core digital commerce journey (Identity -> Product -> Cart -> Order -> Payment -> Fulfillment -> Notification) operates seamlessly without it.
