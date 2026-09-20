# Template Architecture

This document describes the design patterns, package conventions, and cross-cutting layers implemented in this Spring Boot template.

---

## Architecture Flow

The template uses a clean, layered architecture:

```
Controller (REST API)
    │
    ▼ (DTO validation, @PreAuthorize checks)
Service (Transactional Business Logic & Operations)
    │
    ▼ (AOP Auditing, Sensitive Encryption Converter)
Repository (Spring Data JPA)
    │
    ▼
Database (MySQL / H2 Memory)
```

---

## Directory Overview & Core Packages

### 1. `config/`
Houses central bean configurations:
- **`SecurityConfig.java`**: Configures the HTTP security filter chain, HSTS, CORS mapping, Content Security Policy, and session creation rules.
- **`AsyncConfig.java`**: Implements separate thread pools for normal async execution (`taskExecutor`) and mail sending (`mailExecutor`) to prevent SMTP timeouts from blocking system operations.
- **`CacheConfig.java`**: Configures high-performance Redis cache manager with an automatic fallback to an in-memory `ConcurrentMapCacheManager` if Redis is offline.

### 2. `security/`
Handles token-based authentication and request filtering:
- **`JwtAuthenticationFilter.java`**: Intercepts REST requests, extracts the JWT token from the `Authorization` header or HTTP-only cookies, and authenticates the Security context.
- **`RateLimitingFilter.java`**: Integrates **Bucket4j** to limit brute-force requests by IP, applying separate bucket capacities to authentication routes (`/api/auth/**`) and uploads.

### 3. `auth/`
Handles user session lifecycle:
- **`AuthService.java`**: Manages registration, login, logout, and token rotation.
- **`RefreshTokenService.java`**: Tracks session families. If a revoked refresh token is reused, it revokes the entire token family automatically (re-entry protection) and triggers a warning alert.

### 4. `common/`
Common helpers:
- **`api/`**: Automatically wraps all REST controller return payloads in a standardized, front-end friendly `ApiResponse` envelope using `ResponseBodyAdvice`.
- **`crypto/SensitiveDataEncryptionConverter.java`**: A JPA attribute converter that transparently encrypts database columns (e.g. personal Identifiers) using **AES-GCM 256-bit** encryption before writing to disk, and decrypts them on read.
- **`sentry/SentryPlaceholderService.java`**: Logs system exceptions after automatically scrubbing sensitive parameters (email, Aadhaar numbers, card patterns) to prevent PII leaks to external log aggregators.

### 5. `audit/`
- Implements aspect-oriented auditing. By annotating service methods with `@AuditAction("ACTION_NAME")`, the `AuditAspect` dynamically intercepts calls, sanitizes parameters, and logs audit metadata asynchronously to database tables.

### 6. `payment/`
- Generic Razorpay client wrapping. It provides order generation and cryptographic verification. Features a `PaymentSimulator` profile-gate (`!prod`) that prevents simulated sandbox transactions from succeeding on production profiles.

### 7. `captcha/`
- Abstraction over CAPTCHA services. Integrates Cloudflare Turnstile API verify checks with a fallback local dummy verification for test suites.
