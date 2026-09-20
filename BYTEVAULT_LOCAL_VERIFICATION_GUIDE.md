# ByteVault Media Local Verification Guide

This guide provides the exact, reproducible manual instructions to start the microservices locally, verify gateway routing, and perform customer/admin operations.

---

## 1. Prerequisites

- **Java:** OpenJDK 21.0.10 LTS (`java -version`)
- **Maven:** Apache Maven 3.9.11 (`mvn -version`)
- **Node.js:** Node v24.11.1 / npm 11.6.2 (`node -v && npm -v`)
- **Docker / Docker Compose:** `UNAVAILABLE` &rarr; In-memory standalone runtime mode (H2 with PostgreSQL compatibility dialect & Eureka) is used.
- **Database:** In-Memory H2 DB (`jdbc:h2:mem:...;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`)
- **Redis / RabbitMQ / MinIO:** `LIVE INFRASTRUCTURE VERIFICATION: BLOCKED` (Verified via unit/mocked test contexts).

---

## 2. Start Infrastructure

Because external Docker daemon is not installed on the system, start the embedded Eureka Discovery Server first to provide dynamic service location.

### Step 1: Start Discovery Server (Eureka)
```powershell
cd d:\ProductManagementSystem\backend
mvn spring-boot:run -pl discovery-server
```
- **Port:** `8761`
- **Dashboard:** http://localhost:8761

---

## 3. Start Backend Services

Open separate terminals for each service:

### Step 2: Start Auth Service
```powershell
cd d:\ProductManagementSystem\backend
mvn spring-boot:run -pl auth-service "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:auth_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
```
- **Port:** `8081`

### Step 3: Start Product Service
```powershell
cd d:\ProductManagementSystem\backend
mvn spring-boot:run -pl product-service "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:product_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
```
- **Port:** `8084`

### Step 4: Start API Gateway
```powershell
cd d:\ProductManagementSystem\backend
mvn spring-boot:run -pl api-gateway
```
- **Port:** `8080`

---

## 4. Verify Backend Health

| Service | URL | Expected Result |
| :--- | :--- | :--- |
| **Discovery Server** | `http://localhost:8761/eureka/apps` | HTTP 200 (XML/JSON showing AUTH-SERVICE and PRODUCT-SERVICE) |
| **API Gateway** | `http://localhost:8080/actuator/health` | HTTP 200 `{"status":"UP"}` |
| **Public Catalog via Gateway** | `http://localhost:8080/api/v1/products` | HTTP 200 (JSON catalog list) |
| **Categories via Gateway** | `http://localhost:8080/api/v1/categories` | HTTP 200 (JSON categories list) |

---

## 5. Start Frontend

```powershell
cd d:\ProductManagementSystem\frontend\customer-portal
npm install
npm run dev -- --port 5173
```
- **Frontend URL:** http://localhost:5173

---

## 6. Customer Verification

### Step 1: Browse Public Catalog
- Open `http://localhost:5173/catalog`
- **Expected:** Seeded digital and physical products appear with prices, badges, and rating stars.
- **Search & Filter:** Type `Security` or select `E-Books`. Grid filters immediately.

### Step 2: View Product Details
- Click on any product card (e.g. *ByteVault Security Sentinel*).
- **Expected:** Detailed view displays specifications, file formats, license info, and price.

### Step 3: Cart Management
- Click **Add to Cart**.
- Open Cart drawer/page.
- **Expected:** Item is added. Adjust quantity with `+` / `-`, or click remove to clear.

### Step 4: Customer Registration & Login
- Go to `http://localhost:5173/register`
- Fill form: `Alice Customer`, `alice@bytevault.com`, `Password123!`.
- Click **Register**.
- **Expected:** Account created, JWT issued, user logged in with `CUSTOMER` role.

---

## 7. Admin Verification

### Step 1: Admin Authentication
- Log in with default seed admin:
  - **Email:** `admin@bytevault.com`
  - **Password:** `AdminPass123!`
- **Expected:** Returns JWT token with claim `roles: ["ROLE_ADMIN"]`.

### Step 2: Admin Create Category (via Gateway API)
```bash
curl -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer <ADMIN_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Developer Tools", "description": "Compilers and SDKs"}'
```
- **Expected:** HTTP 201 Created with generated category ID.

### Step 3: Admin Create Product (via Gateway API)
```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <ADMIN_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Cloud Security Toolkit",
    "description": "Enterprise automated penetration testing tool.",
    "price": 199.99,
    "productType": "DIGITAL",
    "categoryId": 1,
    "fileName": "toolkit.zip",
    "fileType": "application/zip",
    "fileSize": 10485760,
    "fileVersion": "1.0.0"
  }'
```
- **Expected:** HTTP 201 Created with product UUID.

---

## 8. Security Verification

### A. Missing JWT on Protected Endpoint
- Request: `POST http://localhost:8080/api/v1/products` (No Authorization header)
- **Expected:** `HTTP 401 Unauthorized`.

### B. Header Spoofing Defense
- Request: `POST http://localhost:8080/api/v1/products` with `X-User-Roles: ROLE_ADMIN` but no valid signature.
- **Expected:** `HTTP 401 Unauthorized` (Gateway strips untrusted downstream headers).

### C. Direct Microservice Port Bypass Block
- Request: `GET http://localhost:8084/api/v1/products` (direct to Product Service port 8084).
- **Expected:** `HTTP 403 Forbidden` (`DownstreamSecurityFilter` rejects calls lacking `X-Gateway-Secret`).

### D. Privilege Escalation Defense
- Request: `POST http://localhost:8080/api/v1/auth/register` with `"role": "ADMIN"`.
- **Expected:** `HTTP 403 Forbidden` (Only `ROLE_CUSTOMER` or `ROLE_VENDOR` can self-register).

### E. Customer Calling Admin Route
- Request: `POST http://localhost:8080/api/v1/categories` with customer JWT.
- **Expected:** `HTTP 403 Forbidden`.

---

## 9. Payment Verification

- **Live Provider:** Razorpay
- **Status:** `Razorpay LIVE VERIFICATION: NOT PERFORMED` (Mocked webhook & HMAC-SHA256 unit/integration tested).
- **HMAC Signature Check:** Embedded test `PaymentWebhookTest` validates cryptographic HMAC-SHA256 signatures with 100% pass rate.

---

## 10. Digital Product Verification

- Digital products include file metadata (`fileName`, `fileType`, `fileSize`, `fileVersion`).
- Entitlement downloads are guarded by JWT signature verification.

---

## 11. Physical Product Verification

- Physical products track SKU, weight (`physicalWeight`), dimensions (`physicalDimensions`), and stock status.

---

## 12. Evidence to Capture

- **Screenshots:** Home page, Catalog with active filters, Product Details, Cart, Admin Login.
- **Logs:** Gateway route resolution logs, Auth token generation logs.
- **Terminal Execution:** Node API test runner `node test_apis.js` output table.

---

## 13. Known Limitations

1. External container infrastructure (Docker daemon, Live PostgreSQL, Redis cluster, RabbitMQ broker, MinIO S3) is not installed on this host machine (`LIVE INFRASTRUCTURE VERIFICATION: BLOCKED`).
2. Live credit card / UPI checkout via external Razorpay requires active production API keys and network webhook tunneling (`Razorpay LIVE VERIFICATION: NOT PERFORMED`).
