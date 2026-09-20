# Configuration & Environment Setup

This document lists the required and optional configuration keys used by the application, externalized in `.env.local` for local execution or defined as environment variables in production.

---

## Central Variables

| Environment Variable | Description | Requirement | Dev Fallback / Sandbox |
| :--- | :--- | :--- | :--- |
| `DB_URL` | JDBC database URL path. | **Required** | `jdbc:h2:mem:devdb;...` (H2) |
| `DB_USER` | Database username credentials. | **Required** | `sa` (H2) |
| `DB_PASSWORD` | Database password. | **Required** | (Blank) |
| `JWT_SECRET` | 256-bit secure secret key. | **Required** | Default placeholder |
| `APP_CRYPTO_ENCRYPTION_KEY` | AES GCM 32-byte key. | **Required** | Default placeholder |
| `ADMIN_BOOTSTRAP_ENABLED` | Set `true` to seed the admin account. | Optional | `false` |
| `ADMIN_BOOTSTRAP_EMAIL` | Admin login email. | Required if bootstrap is true | (Blank) |
| `ADMIN_BOOTSTRAP_PASSWORD` | Admin login password. | Required if bootstrap is true | (Blank) |

---

## Optional Service Variables

### 1. Cloudinary (Storage)
- **`CLOUDINARY_URL`**: Cloudinary service connection string (`cloudinary://key:secret@name`). If empty, the system automatically falls back to local uploads (`/uploads/*` directory).
- **`CLOUDINARY_FOLDER_NAME`**: Targeted media folder path inside Cloudinary.

### 2. Razorpay (Payments)
- **`RAZORPAY_KEY_ID`**: Gateway client ID.
- **`RAZORPAY_KEY_SECRET`**: Gateway secret key.
*Note: If set to `dummy` or left blank, the system automatically enables sandbox payment simulation (blocked in production).*

### 3. CAPTCHA
- **`CLOUDFLARE_TURNSTILE_SECRET_KEY`**: Cloudflare Turnstile secret key. Required in production.

### 4. Google OAuth2
- **`GOOGLE_CLIENT_ID`**: OAuth2 Client ID.
- **`GOOGLE_CLIENT_SECRET`**: OAuth2 Client Secret.

### 5. Mail Configuration
- **`SPRING_MAIL_HOST`**: SMTP Host server (default: `smtp.gmail.com`).
- **`SPRING_MAIL_PORT`**: SMTP Port (default: `587`).
- **`SPRING_MAIL_USERNAME`**: SMTP username.
- **`SPRING_MAIL_PASSWORD`**: SMTP password (or Brevo API Key starting with `xkeysib-` for HTTP API dispatch).
- **`APP_MAIL_FROM_NAME`**: Sender display name.
- **`APP_MAIL_FROM_EMAIL`**: Sender email address.
