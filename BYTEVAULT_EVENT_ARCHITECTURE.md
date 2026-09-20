# ByteVault Media - Event-Driven Architecture & Message Flow

ByteVault Media utilizes RabbitMQ for asynchronous event-driven decoupling between domains. This guarantees high availability, eventual consistency, and zero cascade failures across microservices.

---

## 1. Exchanges, Routing Keys & Queues

```
+------------------------------------------------------------------------------------+
|                                    RABBITMQ                                        |
+------------------------------------------------------------------------------------+

  [order.exchange] (Topic)
      ├── routingKey: "order.created"       ──▶ order.created.queue (inventory-service)
      ├── routingKey: "order.paid"          ──▶ order.paid.fulfillment.queue (fulfillment-service)
      │                                     ──▶ order.paid.shipping.queue (shipping-service)
      │                                     ──▶ order.paid.notification.queue (notification-service)
      └── routingKey: "order.cancelled"     ──▶ order.cancelled.queue (inventory-service)

  [payment.exchange] (Topic)
      ├── routingKey: "payment.captured"    ──▶ payment.captured.order.queue (order-service)
      └── routingKey: "refund.issued"       ──▶ refund.notification.queue (notification-service)

  [inventory.exchange] (Topic)
      └── routingKey: "inventory.low_stock" ──▶ inventory.low_stock.queue (product-service)

  [shipping.exchange] (Topic)
      ├── routingKey: "shipping.dispatched" ──▶ shipping.dispatched.queue (notification-service)
      └── routingKey: "shipping.delivered"  ──▶ shipping.delivered.queue (order-service)

  [support.exchange] (Topic)
      └── routingKey: "ticket.created"      ──▶ support.ticket.queue (notification-service)
```

---

## 2. Event Payloads (Canonical Data Contracts)

### `OrderPaidEvent`
```json
{
  "orderId": "7e4c1c67-dac9-4e56-86c3-301dc7382b0d",
  "userId": "3404c820-2fab-4000-96b6-c45ee97fe5d8",
  "customerEmail": "buyer@bytevault.com",
  "customerName": "Alice Johnson",
  "razorpayOrderId": "order_Hk19aLmQ92",
  "razorpayPaymentId": "pay_982bXkLMno",
  "status": "PAID",
  "items": [
    {
      "productId": "41cbbdfa-6f07-4257-a3d8-21d45bc803e7",
      "vendorId": "692484bb-7fb4-4ea6-a7fe-4fa7b8751fa0",
      "productName": "Digital Audio Master Course",
      "sku": "DIG-AUDIO-01",
      "productType": "DIGITAL",
      "unitPrice": 49.99,
      "quantity": 1
    }
  ]
}
```

---

## 3. Consumer Idempotency & Fault Tolerance

Every event listener checks the local database state before applying mutations:
1. **Duplicate Event Protection**: When `OrderPaidEventListener` receives `order.paid`, it queries `fulfillment_db` for an existing license with the same `(orderId, productId)`. If present, the message is acknowledged and skipped immediately.
2. **Dead Letter Queue (DLQ)**: Failed messages after 3 retry attempts are forwarded to `*.dlq` for manual inspection and alerting.
