# ByteVault Media - Security Architecture & Defense-in-Depth

## 1. Authentication & Token Architecture

ByteVault Media enforces stateless, cryptographically-secured user sessions:

```
[ Client ] ──(Credentials)──▶ [ API Gateway ] ──▶ [ auth-service ]
                                                       │
                                  Issues:              │
                                  1. Access Token (15m, HMAC-SHA256 JWT)
                                  2. Refresh Token (7d, Opaque DB/Redis UUID)
                                                       │
[ Client ] ◄──(Bearer JWT + HttpOnly Cookie)───────────┘
```

### JWT Claims Payload Format
```json
{
  "sub": "3404c820-2fab-4000-96b6-c45ee97fe5d8",
  "email": "vendor@bytevault.com",
  "role": "ROLE_VENDOR",
  "iat": 1788590000,
  "exp": 1788590900
}
```

---

## 2. Insecure Direct Object Reference (IDOR) Protections

Downstream microservices verify ownership on every entity access:
- **Order Retrieval (`order-service`)**: `GET /api/v1/orders/{id}` validates `order.getUserId().equals(requesterId) || isAdmin`.
- **Support Tickets (`support-service`)**: `GET /api/v1/support/tickets/{id}` verifies `ticket.getUserId().equals(requesterId) || isAdmin`.
- **Vendor Products (`product-service`)**: `PUT /api/v1/products/vendor/{id}` guarantees `product.getVendorId().equals(requesterId)`.

Violations immediately trigger HTTP 403 `AccessDeniedException` and security audit warning logs.

---

## 3. Perimeter & Inter-Service Security Boundary

1. **Direct Microservice Port Protection**: Downstream services (e.g. `:8085`, `:8086`) inspect incoming HTTP requests via `DownstreamSecurityFilter`. Requests missing a valid `X-Internal-Secret` matching the gateway's shared secret are rejected with `401 Unauthorized`.
2. **Sensitive Data Encryption**: PII (e.g. shipping street address, phone numbers) are encrypted at rest using AES-GCM-256 via `SensitiveDataEncryptionConverter`.
3. **HTML Sanitization**: Product descriptions and support ticket bodies are sanitized against XSS attacks via OWASP-compliant `HtmlSanitizer`.
4. **Zero Hardcoded Secrets**: Secrets (JWT signing keys, database passwords, Razorpay API credentials) are passed strictly via environment variables.
