# ByteVault Media — Local Evidence Matrix

This matrix documents every test case executed against the local running services and test suite. All entries represent actual executions.

---

## 1. Gateway Perimeter & Routing Evidence Matrix

| ID | Feature | How Tested | Expected | Actual | Level | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **GW-01** | Public Catalog Route | `GET http://localhost:8080/api/v1/products` | HTTP 200 + Correlation ID | HTTP 200, `x-correlation-id` returned | Local Runtime | **PASS** |
| **GW-02** | Public Categories Route | `GET http://localhost:8080/api/v1/categories` | HTTP 200 + Category list | HTTP 200, Array of categories | Local Runtime | **PASS** |
| **GW-SEC-01** | Missing JWT Perimeter Rejection | `POST http://localhost:8080/api/v1/products` (No JWT) | HTTP 401 Unauthorized | HTTP 401 Unauthorized | Local Runtime | **PASS** |
| **GW-SEC-02** | Header Spoofing Defense | `POST http://localhost:8080/api/v1/products` with spoofed `X-User-Roles` | HTTP 401 Unauthorized | HTTP 401 Unauthorized | Local Runtime | **PASS** |
| **GW-SEC-03** | Malformed JWT Defense | `POST http://localhost:8080/api/v1/products` with bad token | HTTP 401 Unauthorized | HTTP 401 Unauthorized | Local Runtime | **PASS** |
| **GW-CORS-01** | CORS Preflight OPTIONS | `OPTIONS http://localhost:8080/api/v1/products` with origin `http://localhost:5173` | HTTP 200 + `Access-Control-Allow-Origin` | HTTP 200, `Allow-Origin: http://localhost:5173` | Local Runtime | **PASS** |

---

## 2. Authentication & Identity Evidence Matrix

| ID | Feature | How Tested | Expected | Actual | Level | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **AUTH-01** | Customer Registration | `POST /api/v1/auth/register` with valid payload | HTTP 200, JWT + `ROLE_CUSTOMER` | HTTP 200, JWT + `ROLE_CUSTOMER` | Local Runtime | **PASS** |
| **AUTH-02** | Duplicate Email Conflict | `POST /api/v1/auth/register` with existing email | HTTP 409 Conflict | HTTP 409 Conflict | Local Runtime | **PASS** |
| **AUTH-03** | Role Escalation Rejection | `POST /api/v1/auth/register` with `"role": "ADMIN"` | HTTP 403 Forbidden | HTTP 403 Forbidden | Local Runtime | **PASS** |
| **AUTH-04** | Customer Valid Login | `POST /api/v1/auth/login` with correct password | HTTP 200, Access & Refresh tokens | HTTP 200, valid JWT tokens | Local Runtime | **PASS** |
| **AUTH-05** | Bad Password Rejection | `POST /api/v1/auth/login` with wrong password | HTTP 401 Unauthorized | HTTP 401 Unauthorized | Local Runtime | **PASS** |
| **AUTH-06** | Refresh Token Rotation | `POST /api/v1/auth/refresh` with refresh token | HTTP 200, new token issued | HTTP 200, new rotated refresh token | Local Runtime | **PASS** |
| **AUTH-07** | Admin Authentication | `POST /api/v1/auth/login` with admin credentials | HTTP 200, `ROLE_ADMIN` | HTTP 200, `ROLE_ADMIN` | Local Runtime | **PASS** |

---

## 3. Product & Category Management Evidence Matrix

| ID | Feature | How Tested | Expected | Actual | Level | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **PROD-01** | Admin Create Category | `POST /api/v1/categories` with admin JWT | HTTP 201 Created | HTTP 201 Created, ID returned | Local Runtime | **PASS** |
| **PROD-02** | Admin Create Digital Product | `POST /api/v1/products` with admin JWT | HTTP 201 Created | HTTP 201 Created, UUID returned | Local Runtime | **PASS** |
| **PROD-03** | Admin Update Product | `PUT /api/v1/products/{id}` with admin JWT | HTTP 200 OK | HTTP 200 OK, updated price 139.99 | Local Runtime | **PASS** |
| **PROD-04** | Customer Product Details GET | `GET /api/v1/products/{id}` public query | HTTP 200 OK + metadata | HTTP 200 OK, full specs returned | Local Runtime | **PASS** |
| **PROD-05** | Customer Product Search & Filter | `GET /api/v1/products?search=Sentinel` | HTTP 200 OK + matching items | HTTP 200 OK, item matched | Local Runtime | **PASS** |

---

## 4. Security & Isolation Evidence Matrix

| ID | Feature | How Tested | Expected | Actual | Level | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | Customer Admin API Denial | `POST /api/v1/products` with Customer JWT | HTTP 403 Forbidden | HTTP 403 Forbidden | Local Runtime | **PASS** |
| **SEC-02** | Customer Category Admin Denial | `POST /api/v1/categories` with Customer JWT | HTTP 403 Forbidden | HTTP 403 Forbidden | Local Runtime | **PASS** |
| **SEC-03** | Direct Port 8084 Bypass Block | `GET http://localhost:8084/api/v1/products` bypassing Gateway | HTTP 403 Forbidden | HTTP 403 Forbidden (`DownstreamSecurityFilter`) | Local Runtime | **PASS** |

---

## 5. Payment & Webhook Evidence Matrix

| ID | Feature | How Tested | Expected | Actual | Level | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **PAY-01** | HMAC-SHA256 Signature Verification | Surefire unit test `PaymentWebhookTest` | Accepted on valid signature | Valid signature verified | Unit / Embedded | **PASS** |
| **PAY-02** | Tampered Signature Rejection | Surefire unit test `PaymentWebhookTest` | Rejected on tampered signature | Tampered signature rejected | Unit / Embedded | **PASS** |
| **PAY-03** | Webhook Idempotency | Surefire test `PaymentWebhookTest` duplicate post | Idempotent on repeat callback | Duplicate payment preserved | Unit / Embedded | **PASS** |
| **PAY-04** | Live Razorpay Network Webhook | External Razorpay network transaction | Production payment | Real provider network tunnel not provisioned | Live Infrastructure | **BLOCKED / NOT PERFORMED** |

---

## 6. Maven Test Suite Reconciliation Summary (Phase 14 & 22)

| Module | Member | Tests Run | Failures | Errors | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `common-library` | Supporting (M1) | 14 | 0 | 0 | 0 | **PASS** |
| `discovery-server` | Member 3 | 1 | 0 | 0 | 0 | **PASS** |
| `config-server` | Member 3 | 1 | 0 | 0 | 0 | **PASS** |
| `api-gateway` | Member 1 | 11 | 0 | 0 | 0 | **PASS** |
| `auth-service` | Member 1 | 19 | 0 | 0 | 0 | **PASS** |
| `user-service` | Member 3 | 11 | 0 | 0 | 0 | **PASS** |
| `product-service` | Member 1 | 13 | 0 | 0 | 0 | **PASS** |
| `cart-service` | Member 2 | 9 | 0 | 0 | 0 | **PASS** |
| `order-service` | Member 2 | 14 | 0 | 0 | 0 | **PASS** |
| `payment-service` | Member 1 (Secondary) | 7 | 0 | 0 | 0 | **PASS** |
| `fulfillment-service` | Member 3 | 1 | 0 | 0 | 0 | **PASS** |
| `notification-service` | Member 1 (Secondary) | 3 | 0 | 0 | 0 | **PASS** |
| `inventory-service` | Member 2 | 7 | 0 | 0 | 0 | **PASS** |
| `shipping-service` | Member 2 | 8 | 0 | 0 | 0 | **PASS** |
| `warehouse-service` | Member 2 | 3 | 0 | 0 | 0 | **PASS** |
| **TOTALS** | **All 16 Modules** | **122** | **0** | **0** | **0** | **100% PASS** |
