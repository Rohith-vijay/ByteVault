# Marketplace Platform Backend

This is the core backend for the Enterprise Marketplace Platform, built with a Microservices architecture.

## Tech Stack
*   **Java 21**
*   **Spring Boot 3.3.x**
*   **Spring Cloud 2023.0.x**
*   **Maven**
*   **PostgreSQL** (Database-per-Service)
*   **Redis** (Carts & Wishlists caching)
*   **RabbitMQ** (Asynchronous event-driven communications)

## Modules
1.  `common-library`: Shared DTOs, Exception handlers, JWT helpers.
2.  `discovery-server`: Eureka Discovery Service.
3.  `config-server`: Spring Cloud Config Service.
4.  `api-gateway`: Routing, authentication claims propagation, and rate limiting.
5.  `auth-service`: Authentication, registration, token generation and validation.
6.  `user-service`: User profiles and address records.
7.  `seller-service`: Merchant registration and status approvals.
8.  `product-service`: Categories, products, variants, images, and reviews catalog.
9.  `inventory-service`: Available, reserved, and sold variant stocks management.
10. `cart-service`: Shopping cart and wishlist cache interface.
11. `order-service`: Checkout lifecycle, payments, and coupons management.
12. `fulfillment-service`: Digital tokens delivery and shipping carrier triggers.
13. `shipping-service`: Logistics label simulation.
14. `notification-service`: Mail sending asynchronously.
