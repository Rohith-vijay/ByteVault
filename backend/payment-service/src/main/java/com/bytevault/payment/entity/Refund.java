package com.bytevault.payment.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    private String reason;

    @Column(nullable = false, length = 30)
    private String status; // PENDING, SUCCESS, FAILED

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;
}
