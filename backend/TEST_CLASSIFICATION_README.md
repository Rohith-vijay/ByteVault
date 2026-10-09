# ByteVault Media — System-Wide Test Classification & Verification Matrix (CO-4)

This document maps all automated tests across the ByteVault microservices architecture to their academic categories: **Unit Tests**, **Integration Tests**, **Consumer-Driven Contract Tests**, and **Perimeter Security Tests**.

---

## 1. Test Architecture Overview

```
ByteVault Media Test Suites (CO-4)
├── 1. Unit Tests (Isolated domain logic, repository/client mocks)
│   ├── Identity: AuthServiceTest, UserProfileServiceTest, VendorProfileServiceTest
│   ├── Product: ProductServiceTest, CategoryServiceTest, ProductControllerTest
│   ├── Cart: CartServiceTest
│   ├── Order: OrderServiceTest, OrderControllerTest
│   ├── Payment: PaymentServiceTest, PaymentControllerTest
│   └── Fulfillment: FulfillmentServiceTest, EntitlementServiceTest
├── 2. Integration Tests (Component wiring, transactional rollbacks, state machines)
│   ├── Identity: VendorRegistrationAndPendingStatusTest
│   ├── Product: ProductEndpointTest, ProductAuditTrailIntegrationTest, VendorApprovalIntegrationTest
│   ├── Cart: CartUserIsolationTest (Anti-IDOR, concurrency recovery)
│   ├── Order: OrderStateTransitionIntegrationTest, OrderUserBoundaryIntegrationTest, OrderOutboxSynchronousRetryTest
│   ├── Payment: PaymentGatewayIntegrationTest
│   ├── Fulfillment: FulfillmentReliabilityAndIdempotencyTest (Synchronous outbox retry)
│   ├── Notification: NotificationEndpointTest
│   └── Gateway: GatewayRoutingTest, GatewayCorsSecurityTest
├── 3. Consumer-Driven Contract Tests (CO-4 Spec Requirement)
│   └── Cart -> Product: CartProductContractTest (WireMock consumer contract for ProductClient)
└── 4. Perimeter Security Tests (CO-2 Spec Requirement)
    └── Downstream Perimeter: DownstreamSecurityFilter / PerimeterValidationTests across all services
```

---

## 2. Test Classification by Active Service

| Microservice | Unit Tests | Integration Tests | Contract Tests | Security Tests | Total Tests |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`identity-service`** | 12 | 4 | N/A | Included in Auth Flow | 16 |
| **`product-service`** | 38 | 14 | N/A | Vendor Read/Write Gates | 52 |
| **`cart-service`** | 13 | 6 | 3 (`CartProductContractTest`) | User Isolation & Header Check | 22 |
| **`order-service`** | 22 | 13 | N/A | Anti-IDOR / Outbox Retry | 35 |
| **`payment-service`** | 8 | 4 | N/A | Perimeter Gate | 12 |
| **`fulfillment-service`**| 6 | 4 | N/A | Idempotency & Downstream Gate | 10 |
| **`notification-service`**| 2 | 2 | N/A | Perimeter Gate | 4 |
| **`api-gateway`** | N/A | 11 | N/A | CORS, JWT, TLS/SSL Keystore | 11 |

---

## 3. Quick Run Commands

```bash
# Run contract test in Cart Service:
mvn test -pl cart-service -Dtest=CartProductContractTest

# Run all tests in a specific service:
mvn test -pl identity-service
mvn test -pl product-service
mvn test -pl cart-service
mvn test -pl order-service
mvn test -pl fulfillment-service
mvn test -pl notification-service
mvn test -pl api-gateway

# Run entire test suite across all active modules:
mvn test
```
