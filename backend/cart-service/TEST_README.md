# Cart Service Test Suite Taxonomy & Verification Guide (CO-4)

This document classifies all tests within `cart-service` to satisfy **Course Outcome 4 (Testing & Quality Assurance)**, explicitly distinguishing **Unit**, **Integration**, and **Consumer-Driven Contract** tests.

---

## 1. Test Classification Matrix

| Test Class | Category | Primary Focus & Verification Scope | Test Tooling |
| :--- | :--- | :--- | :--- |
| [`CartServiceTest`](file:///d:/ProductManagementSystem/backend/cart-service/src/test/java/com/bytevault/cart/CartServiceTest.java) | **Unit Test** | Isolated business logic for adding items, updating quantities, clearing cart, calculation of totals, and validation rules. External dependencies (`CartRepository`, `CartItemRepository`, `ProductClient`) are strictly mocked. | JUnit 5, Mockito |
| [`CartUserIsolationTest`](file:///d:/ProductManagementSystem/backend/cart-service/src/test/java/com/bytevault/cart/integration/CartUserIsolationTest.java) | **Integration Test** | Anti-IDOR security boundaries, multi-tenant isolation, concurrent collision recovery, optimistic locking retries, and non-fabrication of downstream security headers in `SecurityContext`. | JUnit 5, Mockito, Spring Security Context |
| [`CartProductContractTest`](file:///d:/ProductManagementSystem/backend/cart-service/src/test/java/com/bytevault/cart/contract/CartProductContractTest.java) | **Contract Test (Consumer-Driven)** | Consumer contract between `Cart Service` and `Product Service` via OpenFeign `ProductClient`. Verifies HTTP contract (`GET /api/v1/products/{id}`), payload schema matching `ProductSummaryResponse`, error code propagation (404 Not Found), and perimeter security injection (`X-Gateway-Secret`). | JUnit 5, Spring Cloud OpenFeign, WireMock Standalone |

---

## 2. Consumer-Driven Contract Test Details (`CartProductContractTest`)

### Background & Objective
In a distributed microservice architecture, services communicate over HTTP APIs. Breaking changes in the producer (`product-service`) can silently cause failures in the consumer (`cart-service`). 

`CartProductContractTest` enforces the consumer contract that `Cart Service` expects from `Product Service`:
1. **Perimeter Security Compliance:** Validates that `ProductClient` sends the required `X-Gateway-Secret` header on every request.
2. **Schema & Field Deserialization:** Ensures that the response payload returned by `GET /api/v1/products/{id}` contains and deserializes all required fields:
   - `id` (UUID)
   - `name` (String)
   - `price` (BigDecimal)
   - `productType` (String)
   - `status` (String)
3. **HTTP Status Handling (200 OK vs 404 Not Found):** Verifies that a valid product returns status 200 and maps correctly to `ProductSummaryResponse`, and a missing product returns 404 resulting in a handled `FeignException.NotFound`.

### Execution Command
```bash
mvn test -pl cart-service -Dtest=CartProductContractTest
```

---

## 3. Full Test Suite Execution

To run all Unit, Integration, and Contract tests for `cart-service`:
```bash
mvn test -pl cart-service
```
**Total Tests:** 21 tests (13 Unit + 6 Integration + 2 Contract), 0 Failures, 0 Errors.
