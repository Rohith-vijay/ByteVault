package com.bytevault.payment.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vendor_ledger", uniqueConstraints = {
    @UniqueConstraint(name = "uk_vendor_order_ledger", columnNames = {"vendor_id", "order_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorLedger extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "net_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 30)
    private String status; // AVAILABLE, PENDING_SETTLEMENT, SETTLED
}
