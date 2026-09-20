# ByteVault Media — Member 1 Git Scope & Ownership Boundary

This document explicitly defines the Git commit and staging boundaries for **Member 1** in the ByteVault Media project.

---

## 1. Selected Member 1 Modules (3 Core Modules)

These modules represent Member 1's core domain responsibilities and will be staged/pushed to Git:

### A. `backend/api-gateway`
- **Ownership:** Member 1
- **Purpose:** Central perimeter gateway handling incoming requests, JWT authentication verification, header stripping/sanitization (preventing spoofing), user role forwarding, correlation ID tracing, and CORS preflight.
- **Key Files:**
  - `backend/api-gateway/src/main/java/com/bytevault/gateway/ApiGatewayApplication.java`
  - `backend/api-gateway/src/main/java/com/bytevault/gateway/config/GatewaySecurityConfig.java`
  - `backend/api-gateway/src/main/java/com/bytevault/gateway/filter/JwtAuthenticationFilter.java`
  - `backend/api-gateway/src/main/resources/application.yml`
  - `backend/api-gateway/pom.xml`
  - `backend/api-gateway/src/test/`

### B. `backend/auth-service`
- **Ownership:** Member 1
- **Purpose:** Identity and access management, user credentials storage, BCrypt password hashing, JWT Access and Refresh token issuance, token rotation, and role hierarchy enforcement.
- **Key Files:**
  - `backend/auth-service/src/main/java/com/bytevault/auth/AuthApplication.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/config/SecurityConfig.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/config/DataSeeder.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/controller/AuthController.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/service/AuthService.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/service/JwtService.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/entity/UserCredentials.java`
  - `backend/auth-service/src/main/java/com/bytevault/auth/repository/UserCredentialsRepository.java`
  - `backend/auth-service/src/main/resources/application.yml`
  - `backend/auth-service/pom.xml`
  - `backend/auth-service/src/test/`

### C. `backend/product-service`
- **Ownership:** Member 1
- **Purpose:** Dual digital and physical product catalog management, category hierarchy, file metadata, pricing, stock validation, public search/filtering, and admin CRUD controls.
- **Key Files:**
  - `backend/product-service/src/main/java/com/bytevault/product/ProductApplication.java`
  - `backend/product-service/src/main/java/com/bytevault/product/config/SecurityConfig.java`
  - `backend/product-service/src/main/java/com/bytevault/product/config/DataSeeder.java`
  - `backend/product-service/src/main/java/com/bytevault/product/controller/ProductController.java`
  - `backend/product-service/src/main/java/com/bytevault/product/controller/AdminProductController.java`
  - `backend/product-service/src/main/java/com/bytevault/product/controller/CategoryController.java`
  - `backend/product-service/src/main/java/com/bytevault/product/service/ProductService.java`
  - `backend/product-service/src/main/java/com/bytevault/product/service/CategoryService.java`
  - `backend/product-service/src/main/java/com/bytevault/product/entity/Product.java`
  - `backend/product-service/src/main/java/com/bytevault/product/entity/Category.java`
  - `backend/product-service/src/main/resources/application.yml`
  - `backend/product-service/pom.xml`
  - `backend/product-service/src/test/`

---

## 2. Supporting Dependencies (Included for Compilation & Runtime)

These artifacts are strictly required to build and run the 3 selected Member 1 modules:

### A. `backend/common-library`
- **Classification:** SUPPORTING DEPENDENCY
- **Why Required:** Provides domain models (`ApiResponse`, `UserRole`, `ProductType`, `ProductStatus`), base audit entity (`BaseAuditableEntity`), enterprise exceptions (`ApiException`, `ResourceNotFoundException`), and internal gateway security filter (`DownstreamSecurityFilter`).

### B. Root `backend/pom.xml`
- **Classification:** SUPPORTING CONFIGURATION
- **Why Required:** Root Maven parent POM defining Spring Boot 3.3.4 parent, Java 21 toolchain, dependency management, and build plugins (`maven-compiler-plugin`).

### C. `frontend/customer-portal`
- **Classification:** SUPPORTING FRONTEND
- **Why Required:** React/Vite customer web application providing user interface for catalog browsing, product details, cart, and authentication communicating through API Gateway (`http://localhost:8080/api/v1`).

### D. Root Documentation & Git Configuration
- **Classification:** SUPPORTING REPOSITORY FILES
- **Files:** `README.md`, `.gitignore`, `MEMBER_1_GIT_SCOPE.md`, `BYTEVAULT_LOCAL_VERIFICATION_GUIDE.md`, `BYTEVAULT_LOCAL_EVIDENCE_MATRIX.md`, `BYTEVAULT_FINAL_LOCAL_VERIFICATION.md`.

---

## 3. Excluded Modules (Kept Local — NOT Staged / NOT Pushed)

### Member 2 Modules (Commerce & Order Lifecycle)
- `backend/cart-service` — Cart state and item management
- `backend/order-service` — Order processing, saga orchestrator, transactional events
- `backend/inventory-service` — Real-time inventory reservations and stock checks
- `backend/shipping-service` — Physical fulfillment, courier integrations, tracking
- `backend/warehouse-service` — Bin allocation, packing, and dispatch workflows

### Member 3 Modules (User Profile & Infrastructure)
- `backend/user-service` — Extended user profiles, KYC, avatar, addresses
- `backend/fulfillment-service` — Digital asset entitlement and secure download link generator
- `backend/discovery-server` — Netflix Eureka service registry (used locally for runtime routing)
- `backend/config-server` — Centralized Spring Cloud Config repository

### Member 1 Secondary Modules (Preserved locally for later phases)
- `backend/payment-service` — Razorpay webhook & payment signature verification
- `backend/notification-service` — Asynchronous email and transactional notifications

---

## 4. Staging Confirmation

All excluded modules remain 100% intact on the local filesystem. Only the approved Member 1 modules and supporting dependencies are staged for Git commit.
