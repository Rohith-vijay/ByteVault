# Security Controls & Hardening Checklist

This template implements strict production-ready security controls based on industry standards (OWASP Top 10).

---

## 1. Authentication & Session Hardening

- **Password Hashing**: Passwords are encrypted using **BCrypt** with a workload factor of 12.
- **HttpOnly Cookies**: JWT access and refresh tokens are returned in HttpOnly cookies, protecting them from XSS-based token theft (scripts cannot read `document.cookie`).
- **SameSite Configuration**: Cookies are set with `SameSite=Lax` to mitigate Cross-Site Request Forgery (CSRF).
- **Secure Flag**: Cookies automatically append the `Secure` flag when running in the production profile, restricting token transmission to SSL/TLS channels.
- **Refresh Token Family Rotation**: Refresh tokens are rotated on every request. If a reused (revoked) refresh token is submitted, the system invalidates all tokens in its family, forcing a logout.

---

## 2. Network & Header Security

- **Strict HSTS**: Forces browsers to communicate only via HTTPS for 1 year (`max-age=31536000; includeSubDomains; preload`).
- **Content Security Policy (CSP)**: Strict default-src policy restricting script, style, image, connect, frame, and media sources.
- **Frame Protection**: Block framing of any page on other domains (`X-Frame-Options: DENY` and `frame-ancestors 'none'`).
- **Referrer Policy**: Set to `strict-origin-when-cross-origin` to limit metadata exposure during cross-domain navigation.
- **Permissions Policy**: Block access to browser features (geolocation, camera, microphone) by default.

---

## 3. Threat Mitigation

- **Request Rate Limiting**: Powered by **Bucket4j**. Blocks brute-force login attempts (10 req/min limit) and limits large file uploads (3 req/min limit). Exceeding these limits returns standard `HTTP 429 Too Many Requests`.
- **Field Encryption**: `SensitiveDataEncryptionConverter` automatically encrypts database columns in **AES-GCM (256-bit)** before storage and decrypts on lookup.
- **PII Log Sanitization**: The `SentryPlaceholderService` scrubs potential PII (emails, cards, Aadhaar card patterns) from exceptions before mock transmission. Console logs use a regex encoder replacer defined in `logback-spring.xml` to prevent credential leakage.
- **Fail Closed CAPTCHA**: In production, the system blocks requests if Cloudflare Turnstile token validation fails or if the Turnstile secret key is not configured.
- **Validation & Sanitization**: Uses Spring Boot Validation (`@NotBlank`, `@Size`, etc.) and `HtmlSanitizer` to sanitize any rich HTML inputs before handling.
- **Sandbox Fail-Closed Gateway**: Sandbox payment/refund options are strictly gated under `!prod` Spring profile checks using a `PaymentSimulator` bean.
