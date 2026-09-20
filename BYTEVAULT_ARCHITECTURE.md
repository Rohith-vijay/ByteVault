# ByteVault Media - System Architecture Specification

## 1. Executive Summary

ByteVault Media is an enterprise e-commerce platform designed to seamlessly handle hybrid physical and digital goods. The platform utilizes a microservices architecture engineered around **Strict Database Isolation**, **Event-Driven Asynchrony (RabbitMQ)**, **Stateless JWT with Dynamic RBAC via Spring Cloud Gateway**, and **High-Throughput Redis Caching**.

The repository is structured into **17 Maven reactor projects: 1 parent POM + 16 child modules** (1 shared common library, 3 infrastructure services, and 12 domain microservices).

---

## 2. Microservices Topology & Bounded Contexts

```
                                  [ CLIENTS ]
             (Web Browsers / Mobile Clients / Vendor Portals / Admin)
                                       │
                                       ▼
                   ┌───────────────────────────────────────┐
                   │    Spring Cloud Gateway (:8080)       │
                   │  - Global JWT Validation & Claims     │
                   │  - Microservice Rate Limiting (Redis) │
                   │  - Internal HMAC Request Signing      │
                   └───────────────────┬───────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          ▼                            ▼                            ▼
┌───────────────────┐        ┌───────────────────┐        ┌───────────────────┐
│   auth-service    │        │   user-service    │        │  product-service  │
│      (:8081)      │        │      (:8082)      │        │      (:8084)      │
│  - JWT & Tokens   │        │  - Profile & KYC  │        │  - Catalog & SKU  │
│  - OAuth2 Login   │        │  - Address Book   │        │  - POI Excel Imp  │
│  - auth_db        │        │  - user_db        │        │  - product_db     │
└───────────────────┘        └───────────────────┘        └───────────────────┘
          │                            │                            │
          ▼                            ▼                            ▼
┌───────────────────┐        ┌───────────────────┐        ┌───────────────────┐
│   cart-service    │        │   order-service   │        │  payment-service  │
│      (:8086)      │        │      (:8087)      │        │      (:8091)      │
│  - Redis Store    │        │  - State Machine  │        │  - Razorpay Pay   │
│  - TTL Isolation  │        │  - Snapshots      │        │  - Vendor Ledger  │
│  - cart_db        │        │  - order_db       │        │  - payment_db     │
└───────────────────┘        └───────────────────┘        └───────────────────┘
          │                            │                            │
          ▼                            ▼                            ▼
┌───────────────────┐        ┌───────────────────┐        ┌───────────────────┐
│fulfillment-service│        │ inventory-service │        │ shipping-service  │
│      (:8088)      │        │      (:8085)      │        │      (:8089)      │
│  - License Keys   │        │  - Atomic Locks   │        │  - Tracking Num   │
│  - Asset Storage  │        │  - Stock Reserv.  │        │  - Carrier Disp.  │
│  - fulfillment_db │        │  - inventory_db   │        │  - shipping_db    │
└───────────────────┘        └───────────────────┘        └───────────────────┘
          │                            │                            │
          ▼                            ▼                            ▼
┌───────────────────┐        ┌───────────────────┐        ┌───────────────────┐
│ warehouse-service │        │  support-service  │        │notification-srv   │
│      (:8092)      │        │      (:8093)      │        │      (:8090)      │
│  - Warehouse Loc  │        │  - Ticket Desk    │        │  - Email Events   │
│  - Capacity Mgmt  │        │  - Message Thread │        │  - Async Workers  │
│  - warehouse_db   │        │  - support_db     │        │  - Stateless      │
└───────────────────┘        └───────────────────┘        └───────────────────┘
```

---

## 3. Communication Protocols

1. **Synchronous REST via OpenFeign**:
   - Internal inter-service queries requiring immediate response (e.g., `PaymentController` verifying order details with `order-service`, `OrderService` querying product details).
   - Protected by internal HMAC security boundary header `X-Internal-Secret`.

2. **Asynchronous Event-Driven Messaging via RabbitMQ**:
   - High-throughput non-blocking cross-domain events.
   - Topics & Exchanges:
     - `order.exchange` -> `order.created`, `order.paid`, `order.cancelled`
     - `payment.exchange` -> `payment.success`, `payment.failed`, `refund.processed`
     - `inventory.exchange` -> `inventory.reserved`, `inventory.low_stock`
     - `fulfillment.exchange` -> `fulfillment.completed`, `license.issued`
     - `shipping.exchange` -> `shipping.dispatched`, `shipping.delivered`
     - `support.exchange` -> `ticket.created`, `ticket.updated`

---

## 4. Frontend & Backend Port Allocation

| Component | Default Port | Primary Protocol / Responsibility |
| :--- | :--- | :--- |
| **Discovery Server (Eureka)** | `8761` | Microservice Registry & Heartbeat Health Check |
| **Config Server** | `8888` | Centralized Spring Cloud Configuration |
| **API Gateway** | `8080` | Non-blocking Gateway, JWT Verification, Rate Limiting |
| **Auth Service** | `8081` | Authentication, Token Issuance, OAuth2 |
| **User Service** | `8082` | User Profiles, Addresses, Preferences |
| **Product Service** | `8084` | Product Catalog, Apache POI Importer, Category Tree |
| **Inventory Service** | `8085` | Concurrency-Safe Stock Reservations |
| **Cart Service** | `8086` | High-Speed Cart Operations backed by Redis |
| **Order Service** | `8087` | Order Orchestration, State Machine, Snapshotting |
| **Fulfillment Service** | `8088` | Digital Asset Security, License Tokens |
| **Shipping Service** | `8089` | Shipment Dispatch, Courier Tracking Numbers |
| **Notification Service** | `8090` | Async Email & Webhook Notifications (Stateless) |
| **Payment Service** | `8091` | Razorpay Gateway, HMAC Signatures, Vendor Earnings |
| **Warehouse Service** | `8092` | Multi-Warehouse Stock & Allocation |
| **Support Service** | `8093` | Customer & Admin Support Operations Desk |
| **Customer / Admin Portal** | `5173` | React 18 + Vite Frontend Application |
