package com.bytevault.fulfillment.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "entitlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entitlement extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(nullable = false)
    private String status; // ACTIVE, EXPIRED, REVOKED

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
