# API Contracts Document (Phase 3)
## Project Title: Digital Product & Physical Product Marketplace Platform

This document establishes the official REST API contracts for all services within the **Marketplace Platform**. All APIs use the version prefix `/api/v1` and handle request/response payloads in `application/json` format (unless streaming a file).

---

## 1. General API Standards

### 1.1 HTTP Status Codes
*   `200 OK`: Success with payload.
*   `201 Created`: Mutation success. Returns the created resource.
*   `202 Accepted`: Asynchronous request accepted for processing.
*   `400 Bad Request`: Input validation failed. Returns error DTO.
*   `401 Unauthorized`: Authentication token is missing, expired, or invalid.
*   `403 Forbidden`: Authenticated user lacks the required role (`@PreAuthorize` failed).
*   `404 Not Found`: Resource does not exist.
*   `409 Conflict`: Business validation or state conflict (e.g., duplicate email, out of stock).
*   `429 Too Many Requests`: Rate limiter triggered.
*   `500 Internal Server Error`: Unexpected system failure.

### 1.2 Error Response Format
All services return a unified error body on non-2xx failures:
```json
{
  "timestamp": "2026-07-21T22:50:31Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for field: email",
  "path": "/api/v1/users/register",
  "correlationId": "8f8b8a8b-4b2a-4a2a-8c2c-8d8d8d8d8d8d",
  "validationErrors": [
    {
      "field": "email",
      "rejectedValue": "invalid-email",
      "message": "Must be a well-formed email address"
    }
  ]
}
```

### 1.3 Pagination & Sorting Schema
All list queries support common parameters:
- `page` (integer, default: 0)
- `size` (integer, default: 20)
- `sort` (string, format: `field,asc` or `field,desc`, default: `createdAt,desc`)

Pagination response envelope format:
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 145,
  "totalPages": 8,
  "isLast": false
}
```

---

## 2. API Contract Specifications

### 2.1 Authentication & Identity Service (`/api/v1/auth`)

#### POST /api/v1/auth/register
- **Description**: Registers a new user account. Defaults to role `CUSTOMER`.
- **Authorization**: Public
- **Request Body**:
```json
{
  "email": "customer@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```
- **Validation**:
  - `email`: Not empty, valid pattern.
  - `password`: Length [8, 50], containing uppercase, lowercase, numbers, and special characters.
  - `firstName`, `lastName`: Not empty, max 50 chars.
- **Responses**:
  - `201 Created`: Returns User Profile DTO.
  - `400 Bad Request`: Input validation failed.
  - `409 Conflict`: Email already in use.

#### POST /api/v1/auth/login
- **Description**: Authenticates user and issues tokens.
- **Authorization**: Public
- **Request Body**:
```json
{
  "email": "customer@example.com",
  "password": "SecurePassword123!"
}
```
- **Responses**:
  - `200 OK`: Returns access tokens. Sets refresh token as `HttpOnly` cookie.
    ```json
    {
      "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
      "tokenType": "Bearer",
      "expiresIn": 900,
      "role": "CUSTOMER"
    }
    ```
    *Cookie Header*: `Set-Cookie: refreshToken=token_uuid_value; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh`
  - `401 Unauthorized`: Invalid credentials.

#### POST /api/v1/auth/refresh
- **Description**: Exchanges a valid Refresh Token (retrieved from Cookie) for a new short-lived Access Token.
- **Authorization**: Public (requires cookie payload)
- **Responses**:
  - `200 OK`: New access token JSON.
  - `401 Unauthorized`: Expired or blacklisted refresh token.

#### POST /api/v1/auth/logout
- **Description**: Invalidates the current session and blacklists the tokens.
- **Authorization**: Authenticated User (`CUSTOMER`, `SELLER`, `ADMIN`)
- **Responses**:
  - `200 OK`: Logout complete.

---

### 2.2 User Profile Service (`/api/v1/users`)

#### GET /api/v1/users/me
- **Description**: Retrieves current logged-in user profile details.
- **Authorization**: Authenticated (`CUSTOMER`, `SELLER`, `ADMIN`)
- **Responses**:
  - `200 OK`:
    ```json
    {
      "id": "3a2b1c0d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
      "email": "john.doe@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "phoneNumber": "+1234567890",
      "addresses": [
        {
          "id": "9a8b7c6d-5e4f-3a2b-1c0d-9e8d7c6b5a4f",
          "addressLabel": "HOME",
          "recipientName": "John Doe",
          "streetLine1": "123 Main St",
          "streetLine2": "Apt 4B",
          "city": "Springfield",
          "stateProvince": "IL",
          "postalCode": "62701",
          "country": "USA",
          "isDefault": true
        }
      ]
    }
    ```

#### POST /api/v1/users/me/addresses
- **Description**: Adds a shipping/billing address.
- **Authorization**: `ROLE_CUSTOMER`
- **Request Body**:
```json
{
  "addressLabel": "OFFICE",
  "recipientName": "John Doe Corp",
  "streetLine1": "456 Corporate Blvd",
  "city": "Chicago",
  "stateProvince": "IL",
  "postalCode": "60601",
  "country": "USA",
  "isDefault": false
}
```
- **Responses**:
  - `201 Created`: Returns the full Address DTO.

#### CMS ENDPOINTS (Admin Control Panel)
*   **GET** `/api/v1/users` - Admins query and search customer records. Supports pagination. (`ROLE_ADMIN`)
*   **PUT** `/api/v1/users/{id}/suspend` - Suspend a customer profile. (`ROLE_ADMIN`)

---

### 2.3 Seller Service (`/api/v1/sellers`)

#### POST /api/v1/sellers/apply
- **Description**: Registers a seller profile for an existing customer. Status starts as `PENDING`.
- **Authorization**: `ROLE_CUSTOMER`
- **Request Body**:
```json
{
  "businessName": "Acme Digital Assets Ltd",
  "businessDescription": "Templates and ebooks shop",
  "taxIdentifier": "US-123456789-T",
  "payoutDetails": "bank_account_iban_placeholder"
}
```
- **Responses**:
  - `202 Accepted`: Request submitted. Returns registration details status `PENDING`.

#### CMS ENDPOINTS (Admin Control Panel)
*   **GET** `/api/v1/sellers` - Query all registered sellers by state (`PENDING`, `APPROVED`, `SUSPENDED`). (`ROLE_ADMIN`)
*   **POST** `/api/v1/sellers/{id}/approve` - Approve seller profile. Changes status to `APPROVED` and adds `ROLE_SELLER` role to user credentials. (`ROLE_ADMIN`)
*   **POST** `/api/v1/sellers/{id}/reject` - Rejects application. Body: `{ "rejectionReason": "string" }`. (`ROLE_ADMIN`)
*   **POST** `/api/v1/sellers/{id}/suspend` - Suspends seller account. (`ROLE_ADMIN`)

---

### 2.4 Product Service (`/api/v1/products`)

#### GET /api/v1/products
- **Description**: Rich search catalog endpoint. Supports filtering and pagination.
- **Authorization**: Public
- **Query Parameters**:
  - `search` (keyword lookup)
  - `categoryId` (hierarchical category match)
  - `minPrice`, `maxPrice` (price bounds)
  - `type` (`PHYSICAL` or `DIGITAL`)
  - `sellerId` (seller filtering)
  - `minRating` (1 to 5)
- **Responses**:
  - `200 OK`: Paginated Product DTO envelope.

#### POST /api/v1/products
- **Description**: Creates a new product catalog entity.
- **Authorization**: `ROLE_SELLER`, `ROLE_ADMIN`
- **Request Body**:
```json
{
  "name": "Spring Boot Cloud Architecture Guide",
  "description": "Comprehensive guide on developing backend microservices.",
  "categoryId": "2d3e4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a",
  "basePrice": 39.99,
  "type": "DIGITAL",
  "downloadUrlMock": "/secure/store/ebooks/spring-boot-guide.pdf",
  "fileSizeBytes": 12582912,
  "licenseRequired": true,
  "images": [
    { "imageUrl": "https://img.example.com/sb-guide.png", "isPrimary": true }
  ],
  "variants": [
    {
      "sku": "GUIDE-SB-PDF",
      "priceAdjustment": 0.00,
      "attributeJson": { "format": "PDF" }
    },
    {
      "sku": "GUIDE-SB-EPUB",
      "priceAdjustment": 5.00,
      "attributeJson": { "format": "EPUB" }
    }
  ]
}
```
- **Responses**:
  - `201 Created`: Returns created Product DTO.

#### POST /api/v1/products/{id}/reviews
- **Description**: Allows a customer to submit a product review.
- **Authorization**: `ROLE_CUSTOMER` (valid purchase check validated downstream via Order Feign client)
- **Request Body**:
```json
{
  "rating": 5,
  "title": "Incredibly Detailed!",
  "comment": "Helped me construct my system context layout."
}
```
- **Responses**:
  - `201 Created`: Returns Review DTO (Status: `PENDING_MODERATION`).

#### CMS ENDPOINTS (Admin Control Panel)
*   **POST** `/api/v1/products/categories` - Create a Category (supports nesting parent/child). (`ROLE_ADMIN`)
*   **GET** `/api/v1/products/reviews/pending` - Query reviews awaiting moderation. (`ROLE_ADMIN`)
*   **POST** `/api/v1/products/reviews/{id}/approve` - Approve review. (`ROLE_ADMIN`)
*   **POST** `/api/v1/products/reviews/{id}/reject` - Reject/delete review. (`ROLE_ADMIN`)

---

### 2.5 Inventory Service (`/api/v1/inventory`)

#### GET /api/v1/inventory/{sku}
- **Description**: Returns stock counts for a variant SKU.
- **Authorization**: `ROLE_SELLER`, `ROLE_ADMIN` (or internal microservice Feign client)
- **Responses**:
  - `200 OK`:
    ```json
    {
      "variantSku": "TSHIRT-BLK-XL",
      "availableQty": 150,
      "reservedQty": 5,
      "soldQty": 45,
      "warehouseCode": "WH-EAST"
    }
    ```

#### PUT /api/v1/inventory/reserve
- **Description**: Reserves quantities of specific SKUs during checkout (Internal communication).
- **Authorization**: Internal Microservice (`X-Gateway-Secret`)
- **Request Body**:
```json
{
  "orderId": "8c9d0e1f-2a3b-4c5d-6e7f-8a9b0c1d2e3f",
  "reservationItems": [
    { "variantSku": "TSHIRT-BLK-XL", "quantity": 1 }
  ]
}
```
- **Responses**:
  - `200 OK`: Reservations updated successfully.
  - `409 Conflict`: Insufficient stock available.

#### CMS ENDPOINTS (Admin Control Panel)
*   **PUT** `/api/v1/inventory/adjust` - Administrative stock overrides. Body: `{ "variantSku": "SKU", "quantityAdjustment": 50 }`. (`ROLE_ADMIN`, `ROLE_SELLER`)

---

### 2.6 Cart & Wishlist Service (`/api/v1/cart`)

#### GET /api/v1/cart
- **Description**: Returns active shopping cart items.
- **Authorization**: `ROLE_CUSTOMER`
- **Responses**:
  - `200 OK`:
    ```json
    {
      "customerId": "3a2b1c0d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
      "items": [
        {
          "productId": "2d3e4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a",
          "variantSku": "GUIDE-SB-PDF",
          "quantity": 1,
          "name": "Spring Boot Cloud Architecture Guide",
          "unitPrice": 39.99
        }
      ],
      "subtotal": 39.99
    }
    ```

#### POST /api/v1/cart/items
- **Description**: Adds an item to the shopping cart.
- **Authorization**: `ROLE_CUSTOMER`
- **Request Body**:
```json
{
  "variantSku": "GUIDE-SB-PDF",
  "quantity": 1
}
```
- **Responses**:
  - `200 OK`: Cart updated.

---

### 2.7 Order Service (`/api/v1/orders`)

#### POST /api/v1/orders
- **Description**: Creates a new order based on active cart items.
- **Authorization**: `ROLE_CUSTOMER`
- **Headers**:
  - `X-Idempotency-Key` (UUIDv4)
- **Request Body**:
```json
{
  "billingAddressId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8d7c6b5a4f",
  "shippingAddressId": "9a8b7c6d-5e4f-3a2b-1c0d-9e8d7c6b5a4f",
  "couponCode": "SUMMER10"
}
```
- **Responses**:
  - `201 Created`: Order placed in `PENDING` state. Returns order metadata.
  - `409 Conflict`: Out of stock or expired coupon.

#### POST /api/v1/orders/{id}/payment
- **Description**: Process simulated payments.
- **Authorization**: `ROLE_CUSTOMER`
- **Headers**:
  - `X-Idempotency-Key` (UUIDv4)
- **Request Body**:
```json
{
  "paymentProvider": "MOCK",
  "paymentMethodToken": "tok_visa_success"
}
```
- **Responses**:
  - `200 OK`: Payment successful. Order transitions to `PAID`.
  - `402 Payment Required`: Transaction failed. Order transitions to `FAILED`.

#### CMS ENDPOINTS (Admin Control Panel)
*   **GET** `/api/v1/orders` - Query and page all platform orders. (`ROLE_ADMIN`)
*   **POST** `/api/v1/orders/{id}/cancel` - Administrative cancellation and refund. (`ROLE_ADMIN`)

---

### 2.8 Fulfillment Service (`/api/v1/fulfillments`)

#### GET /api/v1/fulfillments/download/{token}
- **Description**: Downloads digital purchase files.
- **Authorization**: Public (validation is handled purely by the high-entropy download token signature validation)
- **Responses**:
  - `200 OK`: Streamed binary file bytes. Content-Disposition: attachment.
  - `403 Forbidden`: Token invalid, expired, or maximum download downloads exceeded.

#### CMS ENDPOINTS (Admin Control Panel)
*   **GET** `/api/v1/fulfillments` - Global dashboard of tracking orders, matching carriers. (`ROLE_ADMIN`)
*   **POST** `/api/v1/fulfillments/shipments/{id}/transit` - Updates a package state to out-for-delivery/in-transit. (`ROLE_ADMIN`)

---

# Verification of Phase 3 API Contracts

This API Contract specifications ensure clean RESTful operations, specify explicit input parameter validations, declare RBAC rules for frontend/CMS clients, and support idempotency keys to secure core financial mutations.

Please review these contracts. Once you provide your explicit approval, we will proceed to **Phase 4 – Workspace Creation**.
