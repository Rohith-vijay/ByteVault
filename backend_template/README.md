# Production-Grade Spring Boot Backend Template

A generic, production-ready, security-hardened Spring Boot backend template. It provides a solid foundation for SaaS platforms, Booking engines, E-commerce backends, SIH submissions, internal enterprise tools, or AI integrations.

Derived from a production-hardened charitable trust platform, this template extracts core architectural patterns into a completely reusable, independent base.

---

## Technical Stack & Versions

- **Java**: 21
- **Spring Boot**: 3.5.10
- **Database Migrations**: Flyway (MySQL/H2 ready)
- **Token Security**: JWT (jjwt 0.11.5)
- **Rate Limiting**: Bucket4j (8.10.1)
- **PII Sanitizer**: OWASP Java HTML Sanitizer

---

## What is Included

1. **Authentication Foundation**:
   - Local User Registration, Email Login, Logout.
   - JWT stateless sessions with Cookie/Header dual extraction.
   - Secure HttpOnly cookies (with Lax SameSite).
   - Refresh token rotation (with family token tracking & reuse detection alerts).
   - Extensible Role/Authority architecture (Pre-configured: `USER`, `ADMIN`).
   - Integrated Google OAuth2 user provisioning stub.

2. **Security Hardening**:
   - BCrypt password hashing (strength 12).
   - Rich security headers (HSTS, CSP, X-Frame-Options frame protection, Referrer Policy, Permissions Policy).
   - Request Rate Limiting via Bucket4j (Separate rules for Auth/Uploads).
   - AES-GCM 256-bit database column converter for encrypting PII/sensitive fields.
   - Cloudflare Turnstile CAPTCHA validation abstraction.

3. **Infrastructure Cross-Cuts**:
   - AOP-driven Administrative Auditing (`@AuditAction` annotation) with parameter masking.
   - WebSocket/STOMP private user queues and broadcast channels.
   - Dual-mode Mail Sender (Brevo REST API / standard SMTP).
   - Cloud Storage abstraction with Cloudinary and local backup image optimizer.
   - Razorpay Payment gateway integration abstraction (without business logic locks).
   - Global exception mapper and response envelope advice.

---

## Intentionally Excluded

- **Charity Specific Business Logic**: Donors, Donations, Event management, Volunteers, Assistance applications, and NGO specific seed data are completely excluded.
- **Production Secrets**: No credentials or private keys are hardcoded.

---

## Directory Structure

```
Backend_template/
├── README.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── .gitignore
│   ├── .env.example
│   └── src/
│       ├── main/
│       │   ├── java/com/example/platform/
│       │   │   ├── PlatformBackendApplication.java
│       │   │   ├── audit/        (AOP Auditing)
│       │   │   ├── auth/         (Security Auth endpoints)
│       │   │   ├── captcha/      (Turnstile / Noop services)
│       │   │   ├── common/       (BaseEntity, ApiResponse, HTML Sanitizer, Crypto)
│       │   │   ├── config/       (Cache, Async, WebConfig, SecurityConfig, Seeders)
│       │   │   ├── email/        (SMTP / Brevo API Mailers)
│       │   │   ├── exception/    (Global Exception Handler)
│       │   │   ├── media/        (Cloudinary & Image Optimizer)
│       │   │   ├── notification/ (WebSockets, STOMP push notifications)
│       │   │   ├── payment/      (Razorpay Payments & Simulator)
│       │   │   └── user/         (User entities & Roles)
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── application-dev.properties
│       │       ├── application-prod.properties
│       │       └── db/migration/
│       │           └── V1__init.sql (Core DDL migrations)
│       └── test/
│           └── java/com/example/platform/ (Tests for auth, rate limits, encryption)
└── docs/
    ├── ARCHITECTURE.md
    ├── SECURITY.md
    ├── CONFIGURATION.md
    └── EXTENDING_TEMPLATE.md
```

---

## Quick Start

### 1. Prerequisites
- Java 21 JDK
- Maven 3.8+ (or use global `mvn` cmd)

### 2. Configure Environment
1. Copy `backend/.env.example` to `backend/.env.local`.
2. Update the environment variables (e.g. set `APP_CRYPTO_ENCRYPTION_KEY` to a 32-character string).

### 3. Running Locally
Run with the `dev` profile to use the in-memory H2 database:
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The server starts on port `8080`.
H2 Console is available at: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:devdb`, User: `sa`, no password).

### 4. Running Tests
Verify the entire test suite:
```bash
mvn clean test
```

---

## Documentation Links

For deeper setup and extension details, refer to:
- [ARCHITECTURE.md](docs/ARCHITECTURE.md): Architectural patterns & folder layout.
- [SECURITY.md](docs/SECURITY.md): Implemented security standards & hardening.
- [CONFIGURATION.md](docs/CONFIGURATION.md): Complete list of properties & variables.
- [EXTENDING_TEMPLATE.md](docs/EXTENDING_TEMPLATE.md): Step-by-step guide on adding your own modules.
