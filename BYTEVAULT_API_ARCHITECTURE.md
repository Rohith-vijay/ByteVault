# ByteVault Media - API Architecture & Contract Matrix

## 1. Gateway Route & Forwarding Rules

All external client traffic routes through Spring Cloud API Gateway on port `8080`. The gateway authenticates incoming Bearer JWT tokens, extracts claims (`userId`, `email`, `role`), injects headers (`X-User-Id`, `X-User-Email`, `X-User-Role`), and appends the internal HMAC signature `X-Internal-Secret`.

```
/api/v1/auth/**         -> lb://auth-service
/api/v1/users/**        -> lb://user-service
/api/v1/products/**     -> lb://product-service
/api/v1/categories/**   -> lb://product-service
/api/v1/cart/**         -> lb://cart-service
/api/v1/orders/**       -> lb://order-service
/api/v1/payments/**     -> lb://payment-service
/api/v1/fulfillment/**  -> lb://fulfillment-service
/api/v1/inventory/**    -> lb://inventory-service
/api/v1/shipping/**     -> lb://shipping-service
/api/v1/warehouses/**   -> lb://warehouse-service
/api/v1/support/**      -> lb://support-service
```

---

## 2. Comprehensive Endpoint Matrix

### A. Authentication & Session Management (`auth-service`)
- `POST /api/v1/auth/register` — Customer / Vendor registration.
- `POST /api/v1/auth/login` — Issues Access Token + Rolling Refresh Token.
- `POST /api/v1/auth/refresh` — Generates a new access token using a valid refresh token.
- `POST /api/v1/auth/logout` — Revokes refresh token in database/Redis.
- `POST /api/v1/auth/oauth/callback` — Google OAuth2 token exchange & auto-provisioning.
- `POST /api/v1/auth/forgot-password` — Sends password reset token via Notification Service.
- `POST /api/v1/auth/reset-password` — Validates reset token and sets new BCrypt password hash.

### B. Product & Catalog Management (`product-service`)
- `GET /api/v1/products` — Paginated & filtered product search.
- `GET /api/v1/products/{id}` — Retrieve detailed product metadata.
- `POST /api/v1/products/vendor` — **VENDOR**: Submit new product for approval (`status: PENDING_APPROVAL`).
- `GET /api/v1/products/vendor/my-products` — **VENDOR**: List products owned by authenticated vendor.
- `PUT /api/v1/products/vendor/{id}` — **VENDOR**: Edit owned product details.
- `POST /api/v1/products/admin/import/preview` — **ADMIN**: Upload & validate Apache POI Excel sheet.
- `POST /api/v1/products/admin/import/confirm` — **ADMIN**: Commit validated Excel rows into catalog.
- `PATCH /api/v1/products/admin/{id}/approve` — **ADMIN**: Approve vendor-submitted product.
- `PATCH /api/v1/products/admin/{id}/reject` — **ADMIN**: Reject vendor product with reason notes.

### C. Cart Operations (`cart-service`)
- `GET /api/v1/cart` — Retrieve authenticated user's active cart.
- `POST /api/v1/cart/items` — Add product SKU to cart.
- `PUT /api/v1/cart/items/{productId}` — Update item quantity.
- `DELETE /api/v1/cart/items/{productId}` — Remove item from cart.
- `DELETE /api/v1/cart` — Clear cart upon checkout or user action.

### D. Order & Checkout Workflow (`order-service`)
- `POST /api/v1/orders` — Create new order from active cart items.
- `GET /api/v1/orders/my-orders` — **CUSTOMER**: List user's historical orders.
- `GET /api/v1/orders/{id}` — **CUSTOMER / ADMIN**: Retrieve order details with IDOR protection.
- `GET /api/v1/orders/vendor/my-sales` — **VENDOR**: Retrieve line items attributed to authenticated vendor.
- `PATCH /api/v1/orders/{id}/status` — **INTERNAL / ADMIN**: Update order state machine.

### E. Payment & Ledger Management (`payment-service`)
- `POST /api/v1/payments/create-order` — Create Razorpay payment order.
- `POST /api/v1/payments/verify` — HMAC-SHA256 signature verification & idempotent order confirmation.
- `POST /api/v1/payments/webhook` — Razorpay webhook event processing.
- `GET /api/v1/payments/vendor/earnings` — **VENDOR**: Real-time sales total, commission deducted, net balance.
- `GET /api/v1/payments/vendor/ledger` — **VENDOR**: Historical ledger transaction entries.
- `POST /api/v1/payments/refund` — **ADMIN**: Process idempotent partial/full refund.

### F. Support Ticketing Desk (`support-service`)
- `POST /api/v1/support/tickets` — **CUSTOMER**: Submit new support ticket.
- `GET /api/v1/support/tickets/my-tickets` — **CUSTOMER**: View owned support tickets.
- `GET /api/v1/support/tickets/{id}` — **CUSTOMER / ADMIN**: View ticket message history.
- `POST /api/v1/support/tickets/{id}/messages` — **CUSTOMER / ADMIN**: Add threaded reply.
- `GET /api/v1/support/admin/tickets` — **ADMIN**: Centralized support desk across all users.
- `PATCH /api/v1/support/admin/tickets/{id}/status` — **ADMIN**: Update ticket status/priority/assignee.
