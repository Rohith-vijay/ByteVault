# ByteVault Media — Final Local Verification & Git Submission Report

**Date:** September 4, 2026  
**System:** ByteVault Media Microservices & Customer Portal Platform  
**Target Scope:** Member 1 Core Modules & Supporting Dependencies  

---

## 1. Selected Member 1 Modules

From the complete microservices architecture, the three strongest, most cohesive **Member 1** modules were selected for initial Git submission based on architectural importance, security contribution, demonstration value, and self-contained execution:

| Module | Why Selected | Dependencies | Demo Value |
| :--- | :--- | :--- | :--- |
| **`api-gateway`** | Central perimeter security, reactive JWT validation, downstream header injection (`X-User-Id`, `X-User-Roles`), correlation ID tracing, rate limiting, and CORS handling. | `common-library`, `spring-cloud-starter-gateway` | **Critical:** Entry point for all customer and admin traffic; enforces zero-trust boundary. |
| **`auth-service`** | Identity authority, BCrypt password hashing, JWT Access & Refresh token issuance, token rotation, credentials store, and role validation. | `common-library`, Spring Security, JPA/H2 | **Critical:** Full register/login/refresh/logout cycle; provides JWTs for the entire platform. |
| **`product-service`** | Core commercial domain managing both Digital and Physical products, category hierarchies, pricing, file metadata, and admin catalog management. | `common-library`, Spring Data JPA, H2 | **High:** Primary storefront catalog powering search, filtering, and admin CRUD. |

---

## 2. Supporting Files Included

| Artifact | Classification | Justification |
| :--- | :--- | :--- |
| **`backend/common-library`** | SUPPORTING DEPENDENCY | Houses shared domain models (`ApiResponse`, `UserRole`, `ProductType`, `ProductStatus`), base audit entity (`BaseAuditableEntity`), exception handlers (`GlobalExceptionHandler`), and `DownstreamSecurityFilter`. |
| **`backend/pom.xml`** | SUPPORTING CONFIGURATION | Root Maven parent defining Spring Boot 3.3.4 parent, Java 21 toolchain, dependency versions, and compiler arguments (`<parameters>true</parameters>`). |
| **`frontend/customer-portal`** | SUPPORTING FRONTEND | React 19 / Vite 8 client application delivering the customer UI for browsing, cart, and authentication via Gateway (`http://localhost:8080/api/v1`). |
| **`.gitignore` & `README.md`** | REPOSITORY METADATA | Standard Git hygiene and platform architectural documentation. |
| **`MEMBER_1_GIT_SCOPE.md`** | SCOPE SPECIFICATION | Formally outlines ownership boundaries and excluded module lists. |
| **`BYTEVAULT_LOCAL_VERIFICATION_GUIDE.md`** | VERIFICATION RUNBOOK | Exact manual startup, port mapping, and step-by-step test commands. |
| **`BYTEVAULT_LOCAL_EVIDENCE_MATRIX.md`** | TEST EVIDENCE | Comprehensive execution logs and status results for all 19 API/Gateway test cases and 120 Maven tests. |

---

## 3. Excluded Modules (Kept Untouched in Working Directory)

| Module | Owner | Purpose | Status in Git |
| :--- | :--- | :--- | :--- |
| `cart-service` | Member 2 | Distributed cart state | Kept local / NOT staged |
| `order-service` | Member 2 | Order processing & saga orchestrator | Kept local / NOT staged |
| `inventory-service` | Member 2 | Stock reservations & inventory locks | Kept local / NOT staged |
| `shipping-service` | Member 2 | Physical fulfillment & tracking | Kept local / NOT staged |
| `warehouse-service` | Member 2 | Bin allocations & dispatch | Kept local / NOT staged |
| `user-service` | Member 3 | Extended profile & KYC | Kept local / NOT staged |
| `fulfillment-service` | Member 3 | Digital entitlement downloads | Kept local / NOT staged |
| `discovery-server` | Member 3 | Netflix Eureka discovery | Kept local / NOT staged |
| `config-server` | Member 3 | Spring Cloud Config | Kept local / NOT staged |
| `payment-service` | Member 1 (Secondary) | Razorpay webhook signature handling | Kept local / NOT staged |
| `notification-service` | Member 1 (Secondary) | Async email/push notifications | Kept local / NOT staged |

---

## 4. System Runtime & Verification Status Breakdown

| Subsystem / Area | Verification Level | Actual Status | Details / Evidence |
| :--- | :--- | :--- | :--- |
| **Backend Runtime** | Local Runtime Verified | **RUNNING** | Gateway (`8080`), Auth (`8081`), Product (`8084`), and Discovery (`8761`) running cleanly with zero startup errors. |
| **Frontend Runtime** | Local Runtime Verified | **RUNNING** | Vite 8.2 dev server running on `http://localhost:5173/` (`VITE_USE_MOCK_API=false`). |
| **Frontend &rarr; Gateway &rarr; Backend** | Local Runtime Verified | **VERIFIED** | Browser requests routed through port `8080` with correlation IDs and JWT headers; CORS preflight passed. |
| **Customer E2E Flow** | Local Runtime / API Verified | **VERIFIED** | Registration, Login, Public Catalog GET, Category GET, Search (`?search=Sentinel`), Product Details GET all pass. |
| **Admin E2E Flow** | Local Runtime / API Verified | **VERIFIED** | Admin Login (`admin@bytevault.com`), Admin Create Category (`POST /categories`), Admin Create Product (`POST /products`), Admin Update (`PUT /products/{id}`) all return 200/201. |
| **Security & Perimeter Defense** | Local Runtime Verified | **VERIFIED** | Missing JWT (401), Spoofed Header (401), Malformed JWT (401), Role Escalation (403), Customer calling Admin API (403), and Direct Port 8084 Bypass (403) all enforced. |
| **Digital Commerce Catalog** | Local Runtime Verified | **VERIFIED** | Digital product metadata (`fileName`, `fileType`, `fileSize`, `fileVersion`) persisted and validated. |
| **Physical Commerce Catalog** | Local Runtime Verified | **VERIFIED** | Physical attributes (`physicalSku`, `physicalWeight`, `physicalDimensions`) persisted and validated. |
| **Payment Service** | Unit / Mocked Verified | **EMBEDDED VERIFIED** | HMAC-SHA256 signature verification & duplicate webhook idempotency tested (7/7 tests passed). |
| **Live Infrastructure (Docker/DB/S3)** | Live Infrastructure | **BLOCKED** | Docker daemon, live PostgreSQL container, Redis broker, RabbitMQ, and Live Razorpay tunnel are not available on this host. |
| **Full Maven Test Suite** | Surefire Test Reports | **120/120 PASS** | 120 tests run across all 16 modules (0 failures, 0 errors, 0 skipped). |

---

## 5. Defects Found & Resolved During Local Runtime Standalone Preparation

| ID | Feature | Problem | Root Cause | Fix Applied | Verification | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **DEF-01** | Standalone Database | Microservices failed startup without external PostgreSQL container. | H2 runtime dependency missing in shared classpath. | Added `com.h2database:h2` runtime dependency to `backend/common-library/pom.xml`. | Services launch standalone in PostgreSQL compatibility mode. | **RESOLVED** |
| **DEF-02** | Reflection Parameters | Spring Boot 3 controllers failed endpoint path variable binding without explicit name. | Missing `-parameters` flag in `maven-compiler-plugin`. | Added `<parameters>true</parameters>` to root `backend/pom.xml` and explicit annotations in controllers. | All `@PathVariable` and `@RequestParam` bind reliably. | **RESOLVED** |
| **DEF-03** | Auth Credentials ID | `AuthService.register()` returned `null` user ID. | `credentialsRepository.save()` return value was not captured. | Updated `credentials = credentialsRepository.save(credentials);` before mapping DTO. | Registration returns populated UUID in JWT and user payload. | **RESOLVED** |
| **DEF-04** | Security 401 Handling | Invalid credentials threw unhandled Spring Security exception causing 500 error. | Missing exception handler for `AuthenticationException`. | Added `@ExceptionHandler(AuthenticationException.class)` in `GlobalExceptionHandler.java` returning 401. | Wrong password returns clean `HTTP 401 Unauthorized`. | **RESOLVED** |
| **DEF-05** | Entity Audit Timestamps | `Product` and `Category` entities threw `DataIntegrityViolationException` on `createdAt` nullability. | Missing JPA lifecycle callbacks in `BaseAuditableEntity`. | Added `@PrePersist` and `@PreUpdate` lifecycle methods to set `createdAt` and `updatedAt`. | Entities save with auto-populated timestamps. | **RESOLVED** |
| **DEF-06** | Frontend API Envelope | Frontend expected raw arrays/objects, but backend returned `ApiResponse<T>` envelope. | `apiClient.js` passed `ApiResponse` directly. | Updated `apiClient.js` to unwrap `response.data` and Spring Data `Page.content`. | Catalog and auth state render seamlessly with backend. | **RESOLVED** |

---

## 6. Git Commit Scope

### INCLUDED (Staged for Member 1 Submission)
- **Member 1 Modules:**
  - `backend/api-gateway/`
  - `backend/auth-service/`
  - `backend/product-service/`
- **Supporting Dependencies & Configuration:**
  - `backend/common-library/`
  - `backend/pom.xml`
  - `frontend/customer-portal/`
  - `.gitignore`
  - `README.md`
  - `MEMBER_1_GIT_SCOPE.md`
  - `BYTEVAULT_LOCAL_VERIFICATION_GUIDE.md`
  - `BYTEVAULT_LOCAL_EVIDENCE_MATRIX.md`
  - `BYTEVAULT_FINAL_LOCAL_VERIFICATION.md`

### EXCLUDED (Untouched on local disk — NOT staged)
- **Member 2 Modules:** `cart-service`, `order-service`, `inventory-service`, `shipping-service`, `warehouse-service`
- **Member 3 Modules:** `user-service`, `fulfillment-service`, `discovery-server`, `config-server`
- **Member 1 Secondary Modules:** `payment-service`, `notification-service`

---

## 7. Final Review Readiness Mapping

| Evaluation Area | Implemented Artifacts & Evidence | Readiness Level |
| :--- | :--- | :--- |
| **Review 1: Architecture & API Design** | Microservices boundary separation, reactive Gateway perimeter, centralized JWT issuance, and unified `ApiResponse<T>` contracts. | **100% READY** |
| **Review 2: Core Security & Business Logic** | BCrypt password hashing, JWT Access/Refresh rotation, Gateway header spoofing defense, downstream secret validation (`DownstreamSecurityFilter`), and digital/physical catalog CRUD. | **100% READY** |
| **Review 3: Integration & Testing** | 120/120 Maven Surefire unit/integration tests passing; 19/19 live Gateway and microservice HTTP API tests passing. | **100% READY** |
| **Review 4: Delivery, UI & Verification** | Responsive React/Vite customer portal, comprehensive verification runbooks, and strict Git staging boundaries preserving team ownership. | **100% READY** |
