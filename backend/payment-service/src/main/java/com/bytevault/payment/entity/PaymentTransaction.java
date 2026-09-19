package com.bytevault.payment.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, SUCCESS, FAILED

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "order_sync_status", length = 20)
    @Builder.Default
    private String orderSyncStatus = "PENDING"; // PENDING, SYNCED, FAILED

    @Column(name = "sync_attempts", nullable = false)
    @Builder.Default
    private int syncAttempts = 0;

    @Column(name = "last_sync_error", length = 1000)
    private String lastSyncError;

    @Column(name = "next_sync_retry_at")
    private java.time.LocalDateTime nextSyncRetryAt;
}
