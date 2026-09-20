# 📋 BYTEVAULT MEDIA: COMPLETE API CONTRACT MATRIX

**Document Identifier:** `BYTEVAULT_API_CONTRACT_MATRIX.md`  
**Scope:** Complete Backend Microservice Endpoints vs. Frontend Service Consumer Contracts  
**Status:** FULLY MAPPED & CROSS-VERIFIED

---

## 1. Authentication & Identity (`auth-service` / Port 8081)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `POST` | `/api/v1/auth/register` | Public | `RegisterRequest` (`fullName`, `email`, `password`, `role`) | `AuthenticationResponse` (`token`, `refreshToken`, `user`) | `authService.register` | Frontend maps `fullName` ↔ `name` | VERIFIED |
| `POST` | `/api/v1/auth/login` | Public | `AuthenticationRequest` (`email`, `password`) | `AuthenticationResponse` (`token`, `refreshToken`, `user`) | `authService.login` | Direct 1:1 Match | VERIFIED |
| `POST` | `/api/v1/auth/refresh` | Public | `RefreshTokenRequest` (`refreshToken`) / Cookie | `AuthenticationResponse` (`token`, `refreshToken`, `user`) | `authService.refresh` | Automatic on 401 | VERIFIED |
| `POST` | `/api/v1/auth/oauth` | Public | `OAuthLoginRequest` (`idToken`, `provider`, `providerId`, `email`, `name`) | `AuthenticationResponse` (`token`, `refreshToken`, `user`) | `authService.oauthLogin` | Cryptographic ID Token | VERIFIED |
| `POST` | `/api/v1/auth/forgot-password`| Public | `ForgotPasswordRequest` (`email`) | `ApiResponse<Void>` | `authService.requestPasswordReset` | Mapped `/forgot-password` | VERIFIED |
| `POST` | `/api/v1/auth/reset-password` | Public | `ResetPasswordRequest` (`token`, `newPassword`) | `ApiResponse<Void>` | `authService.resetPassword` | Mapped `newPassword` ↔ `password` | VERIFIED |
| `POST` | `/api/v1/auth/logout` | Public | `RefreshTokenRequest` (`refreshToken`) | `ApiResponse<Void>` | `authService.logout` | Clears tokens + DB revoke | VERIFIED |

---

## 2. User & Customer Profile (`user-service` / Port 8085)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `GET` | `/api/v1/users/me` | Customer/Admin | None (X-User-Id from JWT) | `ApiResponse<UserProfileResponse>` | `userService.getProfile` | Direct 1:1 Match | VERIFIED |
| `PUT` | `/api/v1/users/me` | Customer/Admin | `UpdateProfileRequest` (`name`, `phone`, `bio`, `avatarUrl`)| `ApiResponse<UserProfileResponse>` | `userService.updateProfile` | Direct 1:1 Match | VERIFIED |
| `GET` | `/api/v1/users/addresses` | Customer/Admin | None | `List<AddressDto>` | `userService.getAddresses` | Standard Address Array | VERIFIED |
| `POST` | `/api/v1/users/addresses` | Customer/Admin | `AddressDto` (`street`, `city`, `state`, `zip`, `country`) | `AddressDto` | `userService.addAddress` | Direct 1:1 Match | VERIFIED |

---

## 3. Product Catalog & Digital Storage (`product-service` / Port 8082)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `GET` | `/api/v1/products` | Public | Query: `search`, `category`, `type` | `List<ProductResponse>` | `productService.getProducts` | Direct 1:1 Match | VERIFIED |
| `GET` | `/api/v1/products/{id}` | Public | Path: `id` (UUID) | `ProductResponse` | `productService.getProductById` | Direct 1:1 Match | VERIFIED |
| `POST` | `/api/v1/products` | ADMIN | `CreateProductRequest` | `ProductResponse` | `productService.createProduct` | Direct 1:1 Match | VERIFIED |
| `PUT` | `/api/v1/products/{id}` | ADMIN | `UpdateProductRequest` | `ProductResponse` | `productService.updateProduct` | Direct 1:1 Match | VERIFIED |
| `POST` | `/api/v1/products/{id}/assets` | ADMIN | `MultipartFile file` | `ProductResponse` | Admin Asset Uploader | Uploads to MinIO S3 | VERIFIED |
| `PATCH`| `/api/v1/products/{id}/status` | ADMIN | Param: `status` (`ACTIVE`/`INACTIVE`) | `ProductResponse` | Admin Status Toggle | Direct 1:1 Match | VERIFIED |
| `DELETE`|`/api/v1/products/{id}` | ADMIN | Path: `id` | 204 No Content | Admin Delete Action | Soft Deactivation | VERIFIED |
| `GET` | `/api/v1/categories` | Public | None | `List<CategoryResponse>` | `productService.getCategories` | Direct 1:1 Match | VERIFIED |
| `GET` | `/api/v1/products/{id}/download-url`| Internal (Gateway Secret)| Path: `id` | `String` (Presigned S3 URL) | `fulfillment-service` (Feign) | 15-Minute S3 Link | VERIFIED |

---

## 4. Shopping Cart (`cart-service` / Port 8086)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `GET` | `/api/v1/cart` | Customer/Admin | None (X-User-Id header) | `ApiResponse<CartResponseDto>` | `cartService.getCart` | Unwraps `data` envelope | VERIFIED |
| `POST` | `/api/v1/cart/items` | Customer/Admin | `CartItemDto` (`productId`, `quantity`) | `ApiResponse<CartResponseDto>` | `cartService.addItem` | Redis TTL 7-Day Storage | VERIFIED |
| `PUT` | `/api/v1/cart/items/{productId}`| Customer/Admin | Query: `quantity` | `ApiResponse<CartResponseDto>` | `cartService.updateQuantity`| Authoritative Price Recheck | VERIFIED |
| `DELETE`|`/api/v1/cart/items/{productId}`| Customer/Admin | Path: `productId` | `ApiResponse<CartResponseDto>` | `cartService.removeItem` | Direct 1:1 Match | VERIFIED |
| `DELETE`|`/api/v1/cart` | Customer/Admin | None | `ApiResponse<CartResponseDto>` | `cartService.clearCart` | Clears Redis key | VERIFIED |

---

## 5. Orders & Checkout (`order-service` / Port 8083)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `POST` | `/api/v1/orders` | Customer/Admin | `CreateOrderRequest` (`items`, `customerEmail`, `shippingAddress`) | `ApiResponse<OrderResponse>` | `orderService.createOrder` | Snapshot Price + OrderType | VERIFIED |
| `GET` | `/api/v1/orders/{id}` | Customer/Admin | Path: `id` | `ApiResponse<OrderResponse>` | `orderService.getOrderById` | Customer Ownership Verified | VERIFIED |
| `GET` | `/api/v1/orders` | Customer/Admin | None | `ApiResponse<List<OrderResponse>>` | `orderService.getOrders` | User-Isolated Order List | VERIFIED |
| `GET` | `/api/v1/orders/admin` | ADMIN | None | `ApiResponse<List<OrderResponse>>` | Admin Order Table | Global Orders View | VERIFIED |
| `PUT` | `/api/v1/orders/{id}/status` | ADMIN | Param: `status` (`PAID`/`CANCELLED`/`SHIPPED`) | `ApiResponse<OrderResponse>` | Admin Status Action | @PreAuthorize("hasRole('ADMIN')") | VERIFIED |

---

## 6. Payment Processing & Webhooks (`payment-service` / Port 8087)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `POST` | `/api/v1/payments` | Public | `PaymentOrderRequest` (`amount`, `currency`, `receipt`) | `PaymentOrderResponse` (`orderId`, `amount`, `keyId`) | `paymentService.createRazorpayOrder` | Inits Razorpay Checkout | VERIFIED |
| `POST` | `/api/v1/payments/verify` | Public | `PaymentVerifyRequest` (`razorpayOrderId`, `razorpayPaymentId`, `razorpaySignature`, `dbOrderId`, `userId`) | `Map<String, Object>` (`status: SUCCESS`) | `paymentService.verifyPayment` | HMAC-SHA256 Timing-Safe Check | VERIFIED |
| `POST` | `/api/v1/payments/webhook` | Webhook Signature | Header: `X-Razorpay-Signature`, Raw JSON | `Map<String, Object>` (`status: PROCESSED`) | Razorpay Webhook Engine | Idempotent duplicate check | VERIFIED |

---

## 7. Digital Fulfillment & Secure Downloads (`fulfillment-service` / Port 8084)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `GET` | `/api/v1/downloads/{productId}` | Customer/Admin | Path: `productId` | `ApiResponse<Map<String, String>>` (`downloadUrl`, `fileName`)| `fulfillmentService.getDownloadUrl`| Checks Active Entitlement + Generates 15-min S3 link | VERIFIED |
| `GET` | `/api/v1/fulfillments/my-entitlements`| Customer/Admin | None | `ApiResponse<List<Entitlement>>` | `fulfillmentService.getEntitlements`| Customer Library List | VERIFIED |
| `POST` | `/api/v1/fulfillments/entitlements/{id}/revoke`| ADMIN | Path: `id` | `ApiResponse<Entitlement>` | Admin Revocation Action | Immediately blocks download | VERIFIED |

---

## 8. Physical Commerce Foundations (`inventory-service`, `shipping-service`, `warehouse-service`)

| Backend Method | Backend Path | Auth / Role | Request DTO / Params | Response DTO / Payload | Frontend Consumer Service | Contract Alignment | Status |
|---|---|---|---|---|---|---|:---:|
| `GET` | `/api/v1/inventory/{productId}` | Public | Path: `productId` | `InventoryItem` (`stockQuantity`, `reservedQuantity`, `availableQuantity`)| Catalog & Stock Badge | Atomic DB Conditional Check | VERIFIED |
| `POST` | `/api/v1/shipping/shipments` | ADMIN | `Map<String, String>` (`orderId`, `carrier`, `address`) | `Shipment` (`trackingNumber`, `status`) | Admin Logistics Panel | Tracking number generated | FOUNDATION |
| `GET` | `/api/v1/warehouse/locations/{productId}`| ADMIN | Path: `productId` | `WarehouseLocation` (`bin`, `shelf`, `aisle`)| Admin Warehouse Panel | Spatial Bin Allocation | FOUNDATION |
