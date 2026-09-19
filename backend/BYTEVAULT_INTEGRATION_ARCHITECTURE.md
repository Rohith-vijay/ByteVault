# BYTEVAULT MEDIA — INTEGRATION ARCHITECTURE & DEPENDENCY SPECIFICATION
**Generated Date:** 2026-09-04 | **Architecture Standard:** Enterprise Microservices Distributed Topology  
**Audit Standard:** Strict Empirical Verification & Real Repository Configuration Extraction

---

## 1. System Overview & Dependency Direction

ByteVault Media is an enterprise digital & physical commerce platform structured into 16 child modules + 1 parent POM = 17 Maven reactor projects. The communication architecture combines:
- **Synchronous Ingress:** Clients connect through `api-gateway` (:8080) which authenticates JWTs, enforces rate limits, scrubs headers, and routes traffic via Eureka service discovery (`lb://<service-id>`).
- **Synchronous Internal RPC:** Services invoke downstream services via declarative Spring Cloud OpenFeign clients (`ProductClient`, `OrderClient`).
- **Asynchronous Event Mesh:** Services publish domain events to RabbitMQ topic exchanges (`user.exchange`, `order.exchange`), which are routed to decoupled subscriber queues for asynchronous processing (e.g. user profile creation, digital fulfillment, email notifications).
- **Decoupled Data Stores:** Each microservice owns its dedicated database, with Redis managing transient cart sessions and MinIO/S3 managing protected digital media objects.

```
                                      [ Clients / Browsers ]
                                                │
                                                ▼
                                    ┌───────────────────────┐
                                    │  api-gateway (:8080)  │
                                    │ (Rate Limiting + JWT) │
                                    └───────────┬───────────┘
                                                │
                        ┌───────────────────────┼───────────────────────┐
                        │ lb://                 │ lb://                 │ lb://
                        ▼                       ▼                       ▼
             ┌────────────────────┐   ┌────────────────────┐   ┌────────────────────┐
             │ auth-service :8081 │   │ product-svc  :8083 │   │ cart-service :8084 │
             │ (DB: auth_db)      │   │ (DB: product_db)   │   │ (Cache: Redis)     │
             └─────────┬──────────┘   └─────────▲──────────┘   └─────────┬──────────┘
                       │                        │ Feign                  │ Feign
                       │ user.registered        │ (Catalog lookup)       │ (Price sync)
                       ▼                        │                        ▼
             ┌────────────────────┐             │              ┌────────────────────┐
             │ user-service :8082 │             ├──────────────┤ order-service :8085│
             │ (DB: user_db)      │             │              │ (DB: order_db)     │
             └────────────────────┘             │              └─────────┬──────────┘
                                                │                        │
                                                │                        │ order.paid
                                                │                        ▼
                                                │              ┌────────────────────┐
                                                │              │payment-service:8086│
                                                │              │ (DB: payment_db)   │
                                                │              └─────────┬──────────┘
                                                │                        │
                        ┌───────────────────────┴────────────────────────┼───────────────────────┐
                        │                                                │                       │ order.paid
                        ▼                                                ▼                       ▼
             ┌────────────────────┐                           ┌────────────────────┐  ┌────────────────────┐
             │fulfillment-svc:8087│                           │inventory-svc :8089 │  │notification-s :8088│
             │ (DB: fulfillment)  │                           │ (DB: inventory_db) │  │ (Email / Events)   │
             └─────────┬──────────┘                           └─────────┬──────────┘  └────────────────────┘
                       │                                                │
                       ▼                                                ▼
             ┌────────────────────┐                           ┌────────────────────┐  ┌────────────────────┐
             │shipping-serv :8090 │                           │warehouse-svc :8091 │  │support-serv  :8092 │
             │ (DB: shipping_db)  │                           │ (DB: warehouse_db) │  │ (DB: support_db)   │
             └────────────────────┘                           └────────────────────┘  └────────────────────┘
```

---

## 2. Complete Service Integration & Port Inventory

| Service Name | Port | Database / Store | Redis Usage | RabbitMQ Role | Object Storage | Eureka Role | Config Server | Feign Clients |
|---|:---:|---|---|---|---|:---:|:---:|---|
| `discovery-server` | `8761` | None | None | None | None | Server | Client | None |
| `config-server` | `8888` | None (Native FS) | None | None | None | Client | Server | None |
| `api-gateway` | `8080` | None | In-memory Limiter | None | None | Client | Client | None |
| `auth-service` | `8081` | PostgreSQL (`auth_db`) | None | Publisher (`user.exchange`) | None | Client | Client | None |
| `user-service` | `8082` | PostgreSQL (`user_db`) | None | Consumer (`user.queue.registration`) | None | Client | Client | None |
| `product-service` | `8083` | PostgreSQL (`product_db`) | None | None | MinIO / S3 (`bytevault-assets`) | Client | Client | None |
| `cart-service` | `8084` | None | Redis (`cart:{userId}`) | None | None | Client | Client | `ProductClient` |
| `order-service` | `8085` | PostgreSQL (`order_db`) | None | Publisher (`order.exchange`) | None | Client | Client | `ProductClient` |
| `payment-service` | `8086` | PostgreSQL (`payment_db`) | None | Publisher (`order.exchange`) | None | Client | Client | `OrderClient` |
| `fulfillment-service`| `8087`| PostgreSQL (`fulfillment_db`)| None | Consumer (`order.queue.paid`) | MinIO Presigned S3 | Client | Client | `ProductClient` |
| `notification-service`| `8088`| PostgreSQL (`notification_db`)| None | Consumer (`notification.queue.*`) | None | Client | Client | None |
| `inventory-service` | `8089` | PostgreSQL (`inventory_db`) | None | None | None | Client | Client | None |
| `shipping-service` | `8090` | PostgreSQL (`shipping_db`) | None | None | None | Client | Client | None |
| `warehouse-service`| `8091` | PostgreSQL (`warehouse_db`) | None | None | None | Client | Client | None |

---

## 3. Database Schemas & Isolation

Every microservice maintains strict database isolation in accordance with microservice design patterns:

1. **`auth_db` (`auth-service`)**:
   - `users`: `id` (UUID, PK), `email` (unique), `password_hash`, `role` (`ROLE_CUSTOMER`, `ROLE_ADMIN`), `is_active`, `created_at`, `updated_at`.
   - `refresh_tokens`: `id` (UUID, PK), `user_id` (FK), `token_hash`, `family_id`, `is_revoked`, `expires_at`, `created_at`.
   - `password_reset_tokens`: `id` (UUID, PK), `user_id` (FK), `token_hash`, `is_used`, `expires_at`.

2. **`user_db` (`user-service`)**:
   - `user_profiles`: `id` (UUID, PK), `user_id` (UUID, unique index), `full_name`, `phone_number` (AES-256 encrypted), `address_line1`, `address_line2`, `city`, `postal_code`, `country`, `created_at`, `updated_at`.

3. **`product_db` (`product-service`)**:
   - `categories`: `id` (UUID, PK), `name` (unique), `description`, `parent_id`.
   - `products`: `id` (UUID, PK), `name`, `description`, `price`, `product_type` (`DIGITAL`, `PHYSICAL`), `is_active`, `category_id`, `digital_file_key`, `file_size_bytes`, `sku`, `weight_kg`, `dimensions_cm`, `created_at`, `updated_at`.

4. **`order_db` (`order-service`)**:
   - `orders`: `id` (UUID, PK), `order_number` (unique), `user_id`, `customer_email`, `total_amount`, `status` (`PENDING`, `PAYMENT_PENDING`, `PAID`, `PROCESSING`, `FULFILLED`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `FAILED`), `order_type` (`DIGITAL`, `PHYSICAL`), `shipping_address`, `created_at`, `updated_at`.
   - `order_items`: `id` (UUID, PK), `order_id` (FK), `product_id`, `product_name`, `unit_price`, `quantity`, `subtotal`.

5. **`payment_db` (`payment-service`)**:
   - `payment_transactions`: `id` (UUID, PK), `order_id`, `user_id`, `razorpay_order_id` (unique index), `razorpay_payment_id`, `razorpay_signature`, `amount`, `currency`, `status` (`PENDING`, `SUCCESS`, `FAILED`), `created_at`, `updated_at`.

6. **`fulfillment_db` (`fulfillment-service`)**:
   - `entitlements`: `id` (UUID, PK), `user_id`, `product_id`, `order_id`, `status` (`ACTIVE`, `EXPIRED`, `REVOKED`), `download_count`, `expires_at`, `granted_at`.
   - `download_records`: `id` (UUID, PK), `entitlement_id`, `user_id`, `product_id`, `ip_address`, `user_agent`, `status` (`SUCCESS`, `DENIED`, `EXPIRED`, `REVOKED`), `downloaded_at`.

7. **`inventory_db` (`inventory-service`)**:
   - `inventory_items`: `id` (UUID, PK), `product_id` (unique index), `is_digital`, `is_available`, `quantity`, `reserved_quantity`, `created_at`, `updated_at`.

8. **`shipping_db` (`shipping-service`)**:
   - `shipments`: `id` (UUID, PK), `order_id` (unique), `tracking_number` (unique index), `carrier`, `status` (`PENDING`, `MANIFESTED`, `SHIPPED`, `IN_TRANSIT`, `DELIVERED`, `RETURNED`, `CANCELLED`), `shipping_address`, `created_at`, `updated_at`.

9. **`warehouse_db` (`warehouse-service`)**:
   - `warehouse_locations`: `id` (UUID, PK), `product_id` (unique), `warehouse_code`, `shelf`, `bin`, `created_at`, `updated_at`.

---

## 4. RabbitMQ Distributed Event Mesh

| Exchange Name | Exchange Type | Routing Key | Target Queue | Subscribing Service | Event Payload | Database / Side Effect |
|---|---|---|---|---|---|---|
| `user.exchange` | `topic` | `user.registered` | `user.queue.registration` | `user-service` | `{userId, email, fullName}` | Inserts default `user_profiles` record idempotently. |
| `user.exchange` | `topic` | `user.registered` | `notification.queue.user.registered` | `notification-service` | `{email, fullName}` | Renders & sends welcome email. |
| `user.exchange` | `topic` | `auth.password.reset` | `notification.queue.password.reset` | `notification-service` | `{email, resetToken}` | Renders & sends password reset email with secure link. |
| `order.exchange` | `topic` | `order.created` | `order.queue.created` | *(Audit / Future)* | `{orderId, userId, items, total}` | Published on order placement. |
| `order.exchange` | `topic` | `order.paid` | `order.queue.paid` | `fulfillment-service` | `{orderId, userId, productIds, email}` | Creates active `entitlements` records for purchased products. |
| `order.exchange` | `topic` | `order.paid` | `notification.queue.order.paid` | `notification-service` | `{orderId, customerEmail, customerName, totalAmount}` | Renders & sends order confirmation & download link email. |
| `order.exchange` | `topic` | `payment.failed` | `order.queue.payment.failed` | *(Audit / Sagas)* | `{orderId, reason}` | Notifies subscribers of payment verification failure. |

---

## 5. Synchronous OpenFeign Client Contracts

1. **`order-service` -> `product-service` (`ProductClient`)**:
   - `GET /api/v1/products/{id}`: Retrieves authoritative price, product type (`DIGITAL`/`PHYSICAL`), active status, and name before order creation.
2. **`cart-service` -> `product-service` (`ProductClient`)**:
   - `GET /api/v1/products/{id}`: Verifies item validity, availability, and live price during cart additions.
3. **`payment-service` -> `order-service` (`OrderClient`)**:
   - `PUT /api/v1/orders/{id}/status?status=PAID`: Synchronizes the order status upon HMAC payment verification.
4. **`fulfillment-service` -> `product-service` (`ProductClient`)**:
   - `GET /api/v1/products/{id}/download-url-internal`: Obtains 15-minute MinIO/S3 presigned URL for entitled digital downloads.

---

## 6. Downstream Gateway Security Filter

All microservices inherit `common-library`'s `DownstreamSecurityFilter`:
- **Validation Header:** `X-Gateway-Secret` matching `${app.gateway.secret}`.
- **Enforcement:** Direct external calls bypassing `api-gateway` return HTTP 403 Forbidden (`"Access Forbidden: Direct calls not allowed."`).
- **Claim Extraction:** Parses `X-User-Id`, `X-User-Roles`, and `X-User-Email` set by `api-gateway` to populate Spring Security's `SecurityContextHolder`.
- **Tracing:** `CorrelationIdFilter` and `FeignCorrelationInterceptor` pass `X-Correlation-Id` across all HTTP hops.
