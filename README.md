# ByteVault Media — Cloud-Native Digital Commerce Platform (Review 2)

ByteVault Media is an enterprise cloud-native microservices platform engineered for secure digital asset commerce. It features Domain-Driven Design (DDD), database-per-service isolation in PostgreSQL, synchronous OpenFeign communication with the Transactional Outbox Pattern, multi-vendor 90/10 royalty accounting, and pre-signed HMAC-SHA256 binary asset download streaming.

---

## 1. Prerequisites

Before starting the ByteVault Media platform, ensure the following tools are installed and available on your system `PATH`:

- **Java Development Kit (JDK):** Version 21 (Tested on Eclipse Adoptium OpenJDK 21.0.10).
- **Apache Maven:** Version 3.9+ (`mvn --version`).
- **Node.js & npm:** Node.js v18+ (`node --version`).
- **PostgreSQL Database Server:** Version 13+ running on `localhost:5432` with databases created:
  - `identity_db`, `product_db`, `cart_db`, `order_db`, `payment_db`, `fulfillment_db`.
- **Operating System Shell:** PowerShell 5.1+ or PowerShell 7+ on Windows (or Bash on Linux/macOS).

---

## 2. Environment Configuration (`.env`)

Copy the template configuration file `.env.example` to `.env` in the project root:

```powershell
Copy-Item .env.example .env
```

### Environment Variables & Credentials

> [!IMPORTANT]
> **Demo-Only Credentials Notice:** The default credentials below are provided strictly for local development and academic evaluation. In production environments, override these variables using secure environment variables or a secrets manager.

```ini
# Database Connection Credentials
DB_USER=postgres
DB_PASSWORD=<your_postgres_password>
CART_DB_PASSWORD=<your_postgres_password>

# Zero-Trust Security Perimeter Secret (Injected by Gateway, validated downstream)
GATEWAY_SECRET=platform_default_gateway_shared_secret

# Cryptographic JWT Access Token Signing Key (Min 256-bit secret)
JWT_SECRET=super_secret_jwt_key_that_is_at_least_32_bytes_long_bytevault_2026

# Seed & Test Account Passwords (Demo-Only)
ADMIN_PASSWORD=AdminPass123!
CUSTOMER_PASSWORD=CustomerPass123!
VENDOR1_PASSWORD=VendorPass123!
VENDOR2_PASSWORD=VendorPass123!
```

---

## 3. Platform Execution: Start & Stop

### Standard Start Path (Full 9-Instance Active Stack)
Run the Node.js multi-service supervisor from the project root. It sequentially boots Eureka, all 7 microservices, a secondary load-balanced `product-service` instance, and the API Gateway with built-in auto-restart supervision:

```powershell
node start-active-stack.js
```

### Starting an Individual Microservice
To start, stop, or restart an individual microservice (e.g. for forced failure demos or scaling):

```powershell
# Usage: tools/start-service.ps1 <service-name> [port]
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start-service.ps1 fulfillment-service 8088
powershell -NoProfile -ExecutionPolicy Bypass -File tools/start-service.ps1 product-service 8085
```

### Standard Stop Path
To gracefully shut down the entire stack, terminate the supervisor window (`Ctrl + C`), or execute:

```powershell
Get-Process -Name java | Stop-Process -Force
```

---

## 4. Port Directory & Key URLs

| Service Name | Port | Database | Documentation & Management URLs |
| :--- | :---: | :---: | :--- |
| **Eureka Discovery Server** | `8761` | None | Dashboard: [http://localhost:8761](http://localhost:8761) |
| **Spring Cloud API Gateway**| `8080` | None | Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)<br/>Actuator Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| **Identity Service** | `8081` | `identity_db` | OpenAPI Docs: [http://localhost:8080/v3/api-docs/identity-service](http://localhost:8080/v3/api-docs/identity-service) |
| **Product Service (Primary)**| `8084` | `product_db` | OpenAPI Docs: [http://localhost:8080/v3/api-docs/product-service](http://localhost:8080/v3/api-docs/product-service) |
| **Product Service (Secondary)**| `8085` | `product_db` | Eureka Load-Balanced Replica |
| **Cart Service** | `8086` | `cart_db` | Relational Shopping Cart API |
| **Order Service** | `8087` | `order_db` | OpenAPI Docs: [http://localhost:8080/v3/api-docs/order-service](http://localhost:8080/v3/api-docs/order-service) |
| **Payment Service** | `8091` | `payment_db` | Razorpay Verification & 90/10 Vendor Ledger |
| **Fulfillment Service** | `8088` | `fulfillment_db`| Digital Entitlements & Signed File Streamer |
| **Notification Service** | `8090` | None | Stateless Transactional Email Dispatcher |
| **Frontend Customer Portal** | `5173` | Browser | Web Portal: [http://localhost:5173](http://localhost:5173) |

---

## 5. Demo Seeding & Real Binary Assets

To seed the marketplace with verified demo accounts, categories, and real binary files (valid PDF and ZIP files generated on the fly):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/seed-demo.ps1
```

### Seeded Demo Accounts (All labeled Demo-Only)

| Role | Email | Default Password | Initial Permissions |
| :--- | :--- | :--- | :--- |
| **ADMIN** | `admin@bytevault.com` | `AdminPass123!` | Vendor approval, refund triggers, ledger auditing |
| **VENDOR** | `cloudvendor@bytevault.com`| `VendorPass123!` | Approved vendor; published eBooks and SDKs |
| **VENDOR** | `securityvendor@bytevault.com`| `VendorPass123!`| Approved vendor; published security guides |
| **CUSTOMER** | `customer@bytevault.com` | `CustomerPass123!` | Shopping cart, checkout, instant digital downloads |

---

## 6. Automated Testing & Verification Commands

```powershell
# 1. Run full Maven clean build and unit/integration tests (295 tests, 0 failures)
mvn clean test

# 2. Run automated 11-step verification suite through Gateway
powershell -NoProfile -ExecutionPolicy Bypass -File backend/postman/run-verification.ps1

# 3. Run Section 2 Test Matrix Gaps (Auth, Roles, Downloads, Dual-Instance LB)
powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-matrix-gaps.ps1

# 4. Run Section 2 Deep-Dive (100-request latency metrics, double fulfill idempotency)
powershell -NoProfile -ExecutionPolicy Bypass -File tools/verify-suite2-matrix.ps1

# 5. Run Live Multi-Role Scenarios (Multi-vendor purchase, reconciler backfill, refunds, expiry)
powershell -NoProfile -ExecutionPolicy Bypass -File verify_priority11_live.ps1

# 6. Run Forced Failure Outbox Recovery Test (Kill fulfillment -> order paid -> restart -> fulfilled)
powershell -NoProfile -ExecutionPolicy Bypass -File verify_7b_outbox_retry.ps1
```

---

## 7. Known Limitations & Review 3 Future Scope

The following architectural constraints are intentionally accepted for Review 2:
1. **OAuth Authentication:** Disabled by default (`app.oauth.enabled=false`). Unverified third-party OAuth ID tokens are rejected with HTTP 400.
2. **Stateless Logout:** Access tokens are stateless 15-minute JWTs. Logout revokes the database refresh token; the access token expires naturally within its remaining lifespan.
3. **Per-Instance Rate Limiting:** Sliding-window rate limiting is enforced in-memory per gateway instance.
4. **Physical Products Deactivated:** Review 2 is 100% digital marketplace. Physical inventory, shipping, and warehouse modules compile in the Maven reactor but remain dormant.
5. **Observability:** Distributed tracing (Zipkin) and Prometheus/Grafana dashboards are deferred to Review 3. MDC correlation ID logging is enforced across all services.
6. **Frontend Token Refresh:** The frontend `apiClient.js` does not currently implement an automatic 401 response interceptor. Users whose sessions exceed 15 minutes must re-login.

For full architectural decision rationales, consult [ADR Decision Log](file:///d:/ProductManagementSystem/docs/decision-log.md).
