package com.bytevault.order.entity;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAID,
    PROCESSING,
    READY_FOR_FULFILLMENT,
    FULFILLED,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    FAILED
}
