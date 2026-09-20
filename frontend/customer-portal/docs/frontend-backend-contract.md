# ByteVault Media — Frontend / Backend API Contract

This document specifies the exact API integration points between the **ByteVault Media Customer Portal** frontend and the **API Gateway** microservices backend.

---

## 1. Integration Settings
*   **Base URL (API Gateway)**: `/api/v1` (locally maps to `http://localhost:8080/api/v1`)
*   **Authentication**: Bearer Authorization tokens injected via the client headers:
    `Authorization: Bearer <AccessToken>`
*   **Correlation & Diagnostics**: Outgoing request trace IDs attached to trace errors:
    `X-Correlation-ID: <uuid>`

---

## 2. API Endpoints & Contract Specifications

### A. Authentication Service

#### 1. Registration Flow
*   **Endpoint**: `POST /api/v1/auth/register`
*   **Payload Shape**:
    ```json
    {
      "name": "Customer Name",
      "email": "customer@bytevault.com",
      "password": "password123"
    }
    ```
*   **Success Response (200 OK)**:
    ```json
    {
      "token": "header.payload.signature",
      "user": {
        "id": "usr_1",
        "name": "Customer Name",
        "email": "customer@bytevault.com",
        "role": "CUSTOMER",
        "avatar": "https://images.unsplash.com/..."
      }
    }
    ```

#### 2. Sign In
*   **Endpoint**: `POST /api/v1/auth/login`
*   **Payload Shape**:
    ```json
    {
      "email": "customer@bytevault.com",
      "password": "password123"
    }
    ```
*   **Success Response (200 OK)**: Same as Register.

#### 3. Token Refresh (HttpOnly Cookies / Session Rotation)
*   **Endpoint**: `POST /api/v1/auth/refresh`
*   **Payload Shape**: Empty (relies on active cookies / localStorage token validation)
*   **Success Response (200 OK)**:
    ```json
    {
      "token": "header.newPayload.signature"
    }
    ```

#### 4. Sign Out Wipes
*   **Endpoint**: `POST /api/v1/auth/logout`
*   **Success Response (200 OK / 204 No Content)**

---

### B. User Profile Service

#### 1. Fetch Current User Profile
*   **Endpoint**: `GET /api/v1/users/me`
*   **Authorization**: Required
*   **Success Response (200 OK)**:
    ```json
    {
      "id": "usr_1",
      "name": "Customer Name",
      "email": "customer@bytevault.com",
      "role": "CUSTOMER",
      "avatar": "https://images.unsplash.com/..."
    }
    ```

---

### C. Product Catalog Service

#### 1. List Products (With Filters and Pagination Ready)
*   **Endpoint**: `GET /api/v1/products`
*   **Query Parameters**:
    *   `search` (string) - Filters by title/description match
    *   `category` (string) - Filters by distinct category segment (e.g. `Audio Equipment`)
    *   `type` (enum) - `DIGITAL` or `PHYSICAL`
    *   `minPrice` / `maxPrice` (number)
    *   `minRating` (number)
    *   `inStockOnly` (boolean)
    *   `sortBy` (string) - `trending` | `price_asc` | `price_desc` | `rating` | `newest`
*   **Success Response (200 OK)**:
    ```json
    [
      {
        "id": "prod_1",
        "title": "Product Title",
        "type": "PHYSICAL",
        "price": 199.99,
        "originalPrice": 249.99,
        "image": "https://images.unsplash.com/...",
        "rating": 4.5,
        "ratingCount": 42,
        "inStock": true,
        "category": "Peripherals",
        "description": "Long detailed description...",
        "specs": {
          "material": "Ergonomic CNC Aluminum",
          "dimensions": "32cm x 13cm x 4cm"
        }
      }
    ]
    ```

#### 2. Get Product Details
*   **Endpoint**: `GET /api/v1/products/{id}`
*   **Success Response (200 OK)**: Single product object detailing specifications, faq blocks, and customer reviews.

---

### D. Shopping Cart Service

#### 1. Get Cart Session
*   **Endpoint**: `GET /api/v1/cart`
*   **Success Response (200 OK)**:
    ```json
    {
      "items": [
        {
          "id": "prod_1",
          "title": "Product Title",
          "price": 199.99,
          "quantity": 1,
          "type": "PHYSICAL",
          "image": "https://..."
        }
      ]
    }
    ```

#### 2. Sync Cart Session State
*   **Endpoint**: `POST /api/v1/cart`
*   **Payload Shape**: Same as GET Cart response.
*   **Success Response (200 OK)**: Returned synchronized cart structure.

---

### E. Checkout & Order Service

#### 1. Create Checkout Order
*   **Endpoint**: `POST /api/v1/orders`
*   **Payload Shape**:
    ```json
    {
      "items": [
        {
          "id": "prod_1",
          "title": "Product Title",
          "type": "PHYSICAL",
          "price": 199.99,
          "quantity": 1
        }
      ],
      "shippingAddress": {
        "name": "Recipient Name",
        "street": "128 Commerce Way",
        "city": "San Francisco",
        "state": "CA",
        "zip": "94107",
        "country": "United States"
      },
      "paymentMethod": "card_••••_4242",
      "shippingMethod": "standard"
    }
    ```
*   **Success Response (200 OK)**:
    ```json
    {
      "id": "ord_100234",
      "userId": "usr_1",
      "createdAt": "2026-08-25T14:45:00Z",
      "fulfillmentStatus": "PROCESSING",
      "items": [...],
      "totals": {
        "subtotal": 199.99,
        "tax": 16.00,
        "shipping": 5.99,
        "total": 221.99
      }
    }
    ```

---

### F. Payment Processing Service

#### 1. Authorize Payment Transaction
*   **Endpoint**: `POST /api/v1/payments`
*   **Payload Shape**:
    ```json
    {
      "amount": 221.99,
      "cardNumber": "4242 4242 4242 4242",
      "cardExpiry": "12/28",
      "cardCvc": "123",
      "cardName": "Recipient Name"
    }
    ```
*   **Success Response (200 OK)**:
    ```json
    {
      "transactionId": "txn_889922110",
      "status": "COMPLETED",
      "message": "Payment cleared."
    }
    ```

#### 2. Verify Gateway Payment Signature
*   **Endpoint**: `POST /api/v1/payments/verify`
*   **Payload Shape**:
    ```json
    {
      "transactionId": "txn_889922110"
    }
    ```
*   **Success Response (200 OK)**:
    ```json
    {
      "success": true,
      "verifiedAt": "2026-08-25T14:46:00Z"
    }
    ```

---

### G. Fulfillment / Downloads Cabinet Service

#### 1. List Unlocked Digital Entitlements
*   **Endpoint**: `GET /api/v1/entitlements`
*   **Success Response (200 OK)**:
    ```json
    [
      {
        "id": "dl_992122",
        "productId": "prod_4",
        "title": "Digital Asset Title",
        "fileSize": "42.5 MB",
        "format": "ZIP",
        "status": "active"
      }
    ]
    ```

#### 2. Fetch Secure Temporary Download URL
*   **Endpoint**: `GET /api/v1/downloads/{productId}`
*   **Success Response (200 OK)**:
    ```json
    {
      "downloadUrl": "https://storage.bytevault.com/files/prod_4_secure?token=sigHash...",
      "fileName": "digital_asset_title.zip"
    }
    ```

---

## 3. Error Standard Handling Responses
All services handle normalized JSON error response bodies:
```json
{
  "status": 401,
  "message": "Session verification failed.",
  "details": {}
}
```
*   `401` -> Triggers logout and forces redirect to `/login`.
*   `403` -> Displays unauthorized access notifications.
*   `404` -> Triggers NotFound redirect options.
*   `409` -> Reports transaction or conflict failures (e.g. Card ending in `4444` mock declines).
*   `429` -> Notifies request rate warnings.
