# ByteVault Media - Database Architecture & Schema Isolation

ByteVault Media mandates strict **Database-per-Service Isolation**. No microservice is permitted direct read or write access to another microservice's database. Cross-boundary interactions occur strictly via REST Feign APIs or RabbitMQ domain events.

---

## 1. Complete Data Store Matrix (11 Isolated Service Data Stores)

| Microservice | Database / Data Store Name | Engine | Key Tables / Namespace | Isolation Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **`auth-service`** | `auth_db` | PostgreSQL / H2 | `users`, `roles`, `refresh_tokens`, `oauth_accounts` | Strict auth credential isolation; password hashes never leave this boundary. |
| **`user-service`** | `user_db` | PostgreSQL / H2 | `user_profiles`, `user_addresses`, `kyc_documents` | PII encryption using AES-GCM-256 for sensitive addresses/phone numbers. |
| **`product-service`** | `product_db` | PostgreSQL / H2 | `products`, `categories`, `product_attributes` | SKU uniqueness index, category hierarchy tree, vendor product indexing. |
| **`cart-service`** | `cart_db` | Redis Cluster | Key namespace `cart:{userId}`, TTL-based keys | Ephemeral memory cache with sub-millisecond access and auto-expiration. |
| **`order-service`** | `order_db` | PostgreSQL / H2 | `orders`, `order_items`, `order_status_history` | Immutable item snapshotting at time of purchase (`unit_price`, `sku`, `vendor_id`). |
| **`payment-service`** | `payment_db` | PostgreSQL / H2 | `payment_transactions`, `vendor_ledgers`, `refunds` | Unique constraint on `razorpay_payment_id` for signature idempotency. |
| **`inventory-service`**| `inventory_db` | PostgreSQL / H2 | `inventory_items`, `inventory_reservations` | Conditional atomic update locks preventing race conditions / overselling. |
| **`fulfillment-service`**| `fulfillment_db`| PostgreSQL / H2 | `digital_licenses`, `asset_download_tokens` | Secure token expiry and download attempt throttling. |
| **`shipping-service`** | `shipping_db` | PostgreSQL / H2 | `shipments`, `shipment_events`, `carrier_manifests` | Tracking number indexing and carrier event timeline history. |
| **`warehouse-service`**| `warehouse_db`| PostgreSQL / H2 | `warehouse_locations` | Multi-node physical inventory mapping. |
| **`support-service`**  | `support_db`  | PostgreSQL / H2 | `tickets`, `ticket_messages`, `ticket_history` | Threaded customer support messages and change audit logs. |

*(Note: `notification-service` is stateless and event-driven; it consumes RabbitMQ events to dispatch emails/webhooks and does not maintain a persistent business database).*

---

## 2. Concurrency Control & Atomic Stock Operations

In `inventory-service`, concurrency race conditions during flash sales or simultaneous checkouts are eliminated through atomic conditional SQL statements:

```sql
-- Atomic Stock Reservation Query
UPDATE inventory_items 
SET available_quantity = available_quantity - :qty,
    reserved_quantity = reserved_quantity + :qty,
    updated_at = CURRENT_TIMESTAMP
WHERE product_id = :productId 
  AND available_quantity >= :qty;
```

If the rows affected is `0`, the service instantly detects an out-of-stock condition and throws a `BadRequestException` before any payment transaction or order confirmation can proceed.

---

## 3. Order Snapshot Immutability

In `order-service`, `OrderItem` stores an immutable snapshot of the product state at the moment of order placement:
- `product_id` (UUID)
- `vendor_id` (UUID)
- `product_name` (String)
- `sku` (String)
- `product_type` (Enum: `DIGITAL` or `PHYSICAL`)
- `unit_price` (BigDecimal)
- `quantity` (Integer)
- `subtotal` (BigDecimal)

Subsequent vendor price modifications or SKU changes in `product_db` do **not** affect historical orders or vendor revenue calculations.
