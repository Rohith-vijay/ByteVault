package com.bytevault.user.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vendor_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProfile extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "store_name", nullable = false, length = 100)
    private String storeName;

    @Column(name = "store_slug", nullable = false, unique = true, length = 120)
    private String storeSlug;

    @Column(name = "store_description", columnDefinition = "TEXT")
    private String storeDescription;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "support_email", length = 100)
    private String supportEmail;

    @Column(name = "business_tax_id", length = 50)
    private String businessTaxId;

    @Column(name = "payout_info", columnDefinition = "TEXT")
    private String payoutInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private VendorStatus status = VendorStatus.PENDING_APPROVAL;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
