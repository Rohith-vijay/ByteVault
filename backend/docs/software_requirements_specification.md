# Software Requirements Specification (SRS)
## Project Title: Digital Product & Physical Product Marketplace Platform (Phase 0)

This document establishes the Software Requirements Specification (SRS) for the unified, enterprise-grade Marketplace Platform. It serves as the baseline for **Phase 0 - Requirement Analysis** of the Software Development Life Cycle (SDLC) and has been updated to reflect architectural enhancements ensuring scalability and extensibility.

---

## 1. Introduction

### 1.1 Purpose
The purpose of this document is to define the functional, non-functional, security, and operational requirements of the Marketplace Platform. This platform is designed to handle both **digital assets** (instant fulfillment via secure download token) and **physical products** (asynchronous fulfillment via inventory reservation, shipping, and delivery) utilizing a unified business workflow.

### 1.2 Scope
The system will be architected as a set of decoupled Spring Boot microservices behind a Spring Cloud API Gateway. It uses a database-per-service paradigm, utilizes Eureka for discovery, and leverages Redis for shopping carts/caching and RabbitMQ for asynchronous event-driven flows. The backend will be designed to support an Enterprise CMS (Admin Panel) and client applications entirely through secure REST APIs.

---

## 2. Actors and User Roles

The system recognizes three primary human actors and three system actors:

### 2.1 Human Actors & Roles
*   **Customer (`ROLE_CUSTOMER`)**:
    *   An end-user who browses products, conducts searches, adds items to their wishlist or cart, manages their cart, initiates checkout, executes simulated payments, views order histories, tracks physical deliveries, and downloads purchased digital products.
*   **Seller (`ROLE_SELLER`)**:
    *   A merchant who manages their listings and variants under the platform guidelines, checks stock levels, and monitors fulfillment status.
*   **Administrator (`ROLE_ADMIN`)**:
    *   A high-privileged user who manages platform configurations, reviews and registers/deauthorizes sellers, configures homepage items, moderates customer reviews, manages coupons, and performs maintenance overrides.

### 2.2 System Actors
*   **Payment Gateway Adapter**:
    *   An internal abstract interface simulating payment processors (e.g., Stripe, PayPal, Razorpay) to confirm or reject transactions.
*   **Carrier Logistics Service Mock**:
    *   Simulates external freight/shipping APIs (e.g., FedEx, UPS) to assign tracking numbers and generate delivery status events.
*   **Notification Engine**:
    *   An asynchronous system worker that consumes events and dispatches emails or system logs to users.

---

## 3. Functional Requirements (FRs)

The functional requirements are grouped by microservice boundaries:

### 3.1 Authentication & Profile Services (FR-AUTH-USER)
*   **FR-1.1**: The system MUST support user registration with validation checks.
*   **FR-1.2**: The system MUST authenticate users using JWT. Returns Access Token and Refresh Token.
*   **FR-1.3**: The system MUST support token rotation, allowing clients to exchange a valid Refresh Token for a new Access Token.
*   **FR-1.4**: The system MUST support role-based authorization (`ADMIN`, `SELLER`, `CUSTOMER`) enforced at both the API Gateway and controller levels.
*   **FR-1.5**: The system MUST support user profile updates, including managing **multiple customer addresses** (e.g., `HOME`, `OFFICE`, `OTHER`).
*   **FR-1.6 (Seller Lifecycle)**: Sellers must go through a structured lifecycle state machine: `PENDING` -> `APPROVED` / `SUSPENDED` / `REJECTED`. Only sellers in the `APPROVED` state can list products or receive payouts.

### 3.2 Product Catalog Services (FR-PRODUCT)
*   **FR-2.1**: The system MUST support a unified product model that handles both Physical and Digital products.
*   **FR-2.2 (Product Variants)**: Products must support a 1-to-many relationship with **Product Variants** (e.g., different sizes, colors, capacities). Inventory tracking is tied directly to the specific variant SKU rather than the generic product.
*   **FR-2.3 (Hierarchical Categories)**: Categories must support infinite levels of nesting (e.g., `Electronics -> Laptops -> Gaming`).
*   **FR-2.4 (Product Images)**: A product must support multiple image links, structured as a 1-to-many relation (`Product -> ProductImage`).
*   **FR-2.5 (Customer Reviews)**: Customers who have purchased a product must be able to write reviews, rate products (1-5 stars), and edit or delete their own reviews. Reviews are subject to a state status (`PENDING_MODERATION`, `APPROVED`, `REJECTED`).
*   **FR-2.6**: Customers MUST be able to perform **Rich Search** against the catalog using:
    *   Keyword search
    *   Category filter (including child category inheritance)
    *   Price range bounds
    *   Seller ID filtering
    *   Product type selection (`PHYSICAL` or `DIGITAL`)
    *   Average rating threshold
    *   Sorting (by price, relevance, creation date)

### 3.3 Shopping Cart & Wishlist Services (FR-CART-WISHLIST)
*   **FR-3.1**: Customers MUST be able to add, update, and remove items in their shopping cart.
*   **FR-3.2**: Cart items must point directly to a specific `ProductVariant` SKU.
*   **FR-3.3**: The system MUST support a **Wishlist** feature. Customers can save variants to their wishlist and move them directly to the cart.
*   **FR-3.4**: Shopping carts and wishlists must be stored in Redis for performance, with cart configurations merging upon customer login.

### 3.4 Inventory Services (FR-INVENTORY)
*   **FR-4.1**: The system MUST track stock levels for each specific `ProductVariant` SKU of physical products.
*   **FR-4.2**: The system MUST support concurrent inventory reservations during checkout using optimistic locking.
*   **FR-4.3**: Reserved inventory MUST revert back to available stock if the order payment fails or if the order is cancelled.
*   **FR-4.4**: Digital products/variants MUST bypass stock count checks.

### 3.5 Order & Payment Services (FR-ORDER)
*   **FR-5.1**: Customers MUST be able to place orders from their cart items, validating variant details, prices, and stock levels.
*   **FR-5.2 (Coupon Application Workflow)**:
    *   Customers can apply coupon codes during cart evaluation or checkout.
    *   Coupons support various discount types: fixed amount, percentage, product-specific, or order-wide discounts.
    *   The system evaluates validity (start/end date, min order value) and recalculates the final payable price.
*   **FR-5.3 (Payment Provider Abstraction)**:
    *   The system must define a common `PaymentProvider` interface.
    *   Implementations will include `MockPaymentProvider` (for testing) with extensibility to plug in `StripePaymentProvider` or `RazorpayPaymentProvider` later without changing Order Service core logic.
*   **FR-5.4**: The system transitions order states: `PENDING` -> `PAID` -> `PROCESSING` -> `FULFILLED`/`FAILED`/`CANCELLED`.

### 3.6 Fulfillment Services (FR-FULFILLMENT)
*   **FR-6.1**: The system MUST automatically route paid orders to the appropriate fulfillment pipeline.
*   **FR-6.2** (*Digital Fulfillment*): Generates a secure, high-entropy download token linked to the purchaser, setting expiration parameters and limiting download retry attempts (e.g. 5 times).
*   **FR-6.3** (*Physical Fulfillment*): Triggers logistics booking with carrier mocks, generates shipping tracking numbers, and publishes shipping status events.

### 3.7 Notification Services (FR-NOTIFICATION)
*   **FR-7.1**: Sends transactional emails or logs asynchronously on system state transitions (receipts, download link, shipping updates).

### 3.8 Enterprise CMS (Admin) REST APIs (FR-ADMIN-CMS)
Exposes secure REST endpoints with `ROLE_ADMIN` authentication:
*   **FR-8.1 (Product & Category Management)**: Admins can moderate products, approve listings, and manage the hierarchical categories tree.
*   **FR-8.2 (Inventory Management)**: Globally monitor stock reserves and manually adjust physical variant inventory.
*   **FR-8.3 (Seller Approval & Management)**: Manage seller statuses (`PENDING`, `APPROVED`, `SUSPENDED`, `REJECTED`).
*   **FR-8.4 (Customer Management)**: Manage customer profile accesses.
*   **FR-8.5 (Order & Shipment Management)**: Monitor orders, initiate administrative refunds, assign carriers, and override shipping states.
*   **FR-8.6 (Digital Asset Management)**: Manage files and revoke secure download tokens.
*   **FR-8.7 (Homepage Configuration & Banner Management)**: Edit landing page features, active banners, and promotional content.
*   **FR-8.8 (Coupon & Discount Management)**: CRUD coupon codes and validation rules.
*   **FR-8.9 (Review Moderation)**: Approve or reject reviews flagged for moderation.
*   **FR-8.10 (FAQ & Static Page Content Management)**: Configure FAQs and static page contents.
*   **FR-8.11 (System Configuration & RBAC)**: Manage gateway properties, rate limits, and roles.
*   **FR-8.12 (Audit Logs)**: Writes administrative actions to a tamper-proof audit logger database.
*   *Note: Analytics dashboard queries and reports will be omitted from the initial core implementation scope to prioritize transactional integrity.*

---

## 4. Non-Functional Requirements (NFRs)

*   **NFR-1.1 (Scalability)**: Design for **10 million registered users** and **100,000 concurrent active users**.
*   **NFR-1.2 (Performance)**: Read APIs must achieve a sub-100ms response time at the 95th percentile.
*   **NFR-1.3**: All service instances support horizontal scaling with client-side load balancing.
*   **NFR-1.4**: Virtual threads (Java 21 Thread-per-request / Loom) will be leveraged to handle massive concurrent incoming I/O blocking requests efficiently.
*   **NFR-2.1 (Security)**: All APIs secured via TLS (HTTPS) at the gateway.
*   **NFR-2.2**: Passwords hashed using BCrypt.
*   **NFR-2.3**: Downstream microservices perform local RBAC using propagated Gateway headers.
*   **NFR-2.4**: Input validation enforced on all REST controllers.
*   **NFR-3.1 (Resilience)**: Database-per-service isolation prevents cascading service failures.
*   **NFR-3.2**: Async messaging decoupling for notifications, fulfillment alerts, and shipment logging.
*   **NFR-4.1 (Observability)**: Every service exposes Actuator endpoints.
*   **NFR-4.2**: API Gateway injects a unique Correlation ID (`X-Correlation-ID`) into all requests.

---

## 5. Constraints & Assumptions

*   **Technology Stack**: Java 21, Spring Boot 3.3.x, Spring Cloud, Maven, PostgreSQL, Redis, RabbitMQ.
*   **Data Isolation**: Microservices cannot query each other's databases directly.
*   **Simulations**: Payments and carrier actions are simulated via clean interfaces to mock implementations.
*   **Storage**: Digital files are placed in an isolated, secure directory resembling an S3 bucket interface.
