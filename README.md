# ByteVault Media - Enterprise Full-Stack E-Commerce Platform

ByteVault Media is a production-grade, distributed microservices platform engineered for hybrid e-commerce supporting both **Digital Products** (instant fulfillment, secure signed token access, digital asset storage) and **Physical Products** (multi-warehouse inventory allocation, atomic conditional reservations, tracking number generation, multi-stage delivery state machines).

---

## 🏛️ System Architecture Overview

```
                      +---------------------------------------+
                      |         React 18 + Vite Frontend      |
                      |  (Customer, Vendor & Admin Portals)   |
                      +-------------------+-------------------+
                                          |
                                          | REST / HTTPS (Port 8080)
                                          v
                      +---------------------------------------+
                      |      Spring Cloud API Gateway         |
                      |  (JWT Auth, Rate Limiter, HMAC Guard) |
                      +-------------------+-------------------+
                                          |
        +---------------------------------+---------------------------------+
        |                                 |                                 |
        v                                 v                                 v
+----------------+               +----------------+               +----------------+
|  auth-service  |               |  user-service  |               | product-service|
|   (auth_db)    |               |   (user_db)    |               |  (product_db)  |
+----------------+               +----------------+               +----------------+
        |                                 |                                 |
        v                                 v                                 v
+----------------+               +----------------+               +----------------+
|  cart-service  |               | order-service  |               | payment-service|
| (Redis Cluster)|               |   (order_db)   |               |  (payment_db)  |
+----------------+               +----------------+               +----------------+
        |                                 |                                 |
        v                                 v                                 v
+----------------+               +----------------+               +----------------+
|fulfillment-srv |               |inventory-srv   |               | shipping-srv   |
|(fulfillment_db)|               | (inventory_db) |               | (shipping_db)  |
+----------------+               +----------------+               +----------------+
        |                                 |                                 |
        v                                 v                                 v
+----------------+               +----------------+               +----------------+
| warehouse-srv  |               | support-service|               |notification-srv|
| (warehouse_db) |               |  (support_db)  |               | (Event-Driven) |
+----------------+               +----------------+               +----------------+
```

---

## 👥 Role-Based Access Control (RBAC) Matrix

ByteVault strictly defines and enforces 3 enterprise roles across all endpoints and front-end portals:

| Role | Permissions & Functional Scope | Frontend Access |
| :--- | :--- | :--- |
| **`CUSTOMER`** | Product catalog browsing, Redis-backed carts, Razorpay checkout, digital license downloads, physical shipment tracking, order history, customer support ticketing. | Customer Portal (`/`, `/catalog`, `/cart`, `/checkout`, `/account`) |
| **`VENDOR`** | Product catalog creation & management (draft submission), order fulfillment tracking, vendor sales analytics, revenue ledger & commission breakdowns. | Vendor Dashboard (`/vendor`) |
| **`ADMIN`** | Platform oversight, vendor product approval/rejection, Apache POI Excel bulk product import, platform-wide transactions, refund processing, centralized customer support operations desk. | Admin Portal (`/admin`) |

---

## 💾 Database Isolation (11 Isolated Service Data Stores)

Each microservice adheres strictly to the **Database-per-Service** architectural pattern:
1. `auth_db` — User credentials, BCrypt hashes, Refresh Tokens, OAuth registrations.
2. `user_db` — Customer/Vendor profiles, shipping addresses, KYC metadata.
3. `product_db` — Categories, digital/physical products, SKU metadata, vendor attribution, approval state machines.
4. `cart_db` (Redis) — High-throughput session-isolated customer cart caches with TTL (`cart:{userId}`).
5. `order_db` — Orders, line item snapshots, multi-state transition workflows.
6. `payment_db` — Payment transactions, Razorpay idempotency signatures, vendor earnings ledgers, refunds.
7. `inventory_db` — Stock levels, atomic reservations (`reserved_quantity`), threshold alerts.
8. `fulfillment_db` — Digital licenses, secure asset access tokens, download tracking.
9. `shipping_db` — Physical parcel manifests, carrier assignments, tracking updates.
10. `warehouse_db` — Physical warehouse locations, storage capacities, inventory distribution.
11. `support_db` — Customer support tickets, message threads, status history, admin assignment.

*(Note: `notification-service` is stateless and event-driven, operating without a persistent database).*

---

## 🚀 Key Modules & Structure

The repository consists of **17 Maven reactor projects: 1 parent POM + 16 child modules**:
- **`common-library`**: Shared DTOs, auditable entities, crypto utilities (AES-GCM-256), global exception handler, downstream security interceptors.
- **`discovery-server`**: Netflix Eureka discovery registry with heartbeat self-preservation.
- **`config-server`**: Spring Cloud Centralized Configuration Server.
- **`api-gateway`**: Non-blocking Spring Cloud Gateway with JWT extraction, role forwarding, and HMAC internal security boundary.
- **`auth-service`**: JWT issue/validation, rolling refresh tokens, OAuth2 integration.
- **`user-service`**: User profiles, address book, encrypted contact metadata.
- **`product-service`**: Multi-vendor product lifecycle, Apache POI bulk Excel importer, approval workflow.
- **`cart-service`**: High-performance cart management leveraging Redis data structures.
- **`order-service`**: Complete order state machine with IDOR defense and immutable line item snapshotting.
- **`payment-service`**: Razorpay checkout initialization, HMAC-SHA256 signature verification, idempotent processing, vendor ledgers, and refunds.
- **`fulfillment-service`**: Digital license generation, tokenized download URLs.
- **`inventory-service`**: Atomic DB-level conditional locks (`WHERE available_quantity >= :qty`).
- **`shipping-service`**: Carrier manifest creation, tracking generation, shipping states.
- **`warehouse-service`**: Multi-location inventory routing and capacity management.
- **`support-service`**: Dedicated support ticketing microservice with threaded messages and audit logs.
- **`notification-service`**: Asynchronous email dispatcher (Welcome, Order Confirmation, Shipping, Password Reset).

---

## 🧪 Authoritative Verification Summary

### Backend Unit & Integration Tests (`mvn clean test`)
```
------------------------------------------------------------------------
Reactor Summary for ByteVault Media Backend Parent 1.0.0-SNAPSHOT:
------------------------------------------------------------------------
ByteVault Media Backend Parent ..................... SUCCESS
Common Library (14 tests) .......................... SUCCESS
Discovery Server (1 test) .......................... SUCCESS
Config Server (1 test) ............................. SUCCESS
API Gateway (11 tests) ............................. SUCCESS
Auth Service (19 tests) ............................ SUCCESS
User Service (4 tests) ............................. SUCCESS
Product Service (13 tests) ......................... SUCCESS
Cart Service (9 tests) ............................. SUCCESS
Order Service (14 tests) ........................... SUCCESS
Payment Service (7 tests) .......................... SUCCESS
Fulfillment Service (7 tests) ...................... SUCCESS
Notification Service (3 tests) ..................... SUCCESS
Inventory Service (6 tests) ........................ SUCCESS
Shipping Service (8 tests) ......................... SUCCESS
Warehouse Service (3 tests) ........................ SUCCESS
Support Service (6 tests) .......................... SUCCESS
------------------------------------------------------------------------
Total Tests Executed: 126 | Passed: 126 | Failures: 0 | Errors: 0 | Skipped: 0 [100% PASS]
------------------------------------------------------------------------
```

### Frontend Build
```
vite build: ✓ 11806 modules transformed. [0 Errors, 0 Warnings in 6.26s]
```

---

## 🛠️ Quick Start & Local Execution

See [BYTEVAULT_REVIEW_RUNBOOK.md](file:///d:/ProductManagementSystem/BYTEVAULT_REVIEW_RUNBOOK.md) for full commands and demonstration flows.
