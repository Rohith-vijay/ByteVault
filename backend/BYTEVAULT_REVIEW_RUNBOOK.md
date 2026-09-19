# ByteVault Media - Reviewer Runbook

This runbook provides exact, reproducible instructions for compiling, testing, and running ByteVault Media.

---

## 1. Prerequisites

- **Java Development Kit**: OpenJDK 21 (LTS)
- **Build Tool**: Apache Maven 3.9+
- **Frontend Runtime**: Node.js v18+ (tested on Node v24) and npm
- **Databases & Middleware (Production)**: PostgreSQL 15+, Redis 7+, RabbitMQ 3.12+
  *(Note: Backend services include H2 in-memory fallbacks for zero-dependency local development and automated testing)*

---

## 2. Automated Test Suite Execution

Run all unit, integration, and security tests across all 17 Maven reactor projects:

```bash
cd backend
mvn clean test
```

### Authoritative Surefire Results
- **Reactor Projects**: 17 (1 parent POM + 16 child modules)
- **Total Tests Executed**: 126
- **Passed**: 126
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Result**: `BUILD SUCCESS`

---

## 3. Frontend Production Build

Compile the React 19 + Vite frontend bundle:

```bash
cd frontend/customer-portal
npm install
npm run build
```

**Result**: 11,806 modules transformed, 0 errors in ~5.25s.

---

## 4. Minimum Local Demo (Lightweight Zero-Dependency Mode)

In this mode, services run locally with in-memory H2 databases:

```bash
# Terminal 1: Discovery Server (Eureka on :8761)
cd backend && mvn spring-boot:run -pl discovery-server

# Terminal 2: API Gateway (Spring Cloud Gateway on :8080)
cd backend && mvn spring-boot:run -pl api-gateway

# Terminal 3: Auth Service (Port :8081)
cd backend && mvn spring-boot:run -pl auth-service "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:auth_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"

# Terminal 4: Product Service (Port :8083)
cd backend && mvn spring-boot:run -pl product-service "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:product_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"

# Terminal 5: Support Service (Port :8092)
cd backend && mvn spring-boot:run -pl support-service "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:h2:mem:support_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL --spring.datasource.driver-class-name=org.h2.Driver --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"

# Terminal 6: Frontend Development Server (Port :5173)
cd frontend/customer-portal && npm run dev
```

---

## 5. User Demonstration Flows

Open `http://localhost:5173` in your browser:

### Flow A: Customer Experience
1. **Browse Catalog**: Visit `/catalog` to view digital and physical products.
2. **Product Details**: Click any item (e.g. *Cloud Architecture Handbook* or *ByteVault Titanium Key*).
3. **Cart Operations**: Add items to cart, modify quantities at `/cart`.
4. **Checkout**: Proceed to `/checkout` (simulated Razorpay payment gateway).
5. **Customer Support**: Navigate to `/account` -> Support Tickets -> Submit ticket and post threaded replies.

### Flow B: Vendor Experience
1. **Login as Vendor**: Role `ROLE_VENDOR`.
2. **Access Dashboard**: Visit `/vendor` to view product catalog, submit new draft products for approval, and review sales earnings ledgers.

### Flow C: Admin Experience
1. **Login as Admin**: Role `ROLE_ADMIN`.
2. **Access Admin Desk**: Visit `/admin` to review pending vendor products (Approve/Reject), access the Support Operations Desk, and use the Apache POI Excel Bulk Product Importer.
