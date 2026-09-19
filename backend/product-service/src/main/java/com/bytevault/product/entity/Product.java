package com.bytevault.product.entity;

import com.bytevault.common.entity.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vendor_id")
    private UUID vendorId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(unique = true)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 500)
    private String tags;

    // Digital configurations
    private String fileStorageKey;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileVersion;
    private String fileChecksum;

    // Physical specifications
    private String physicalSku;
    private Double physicalWeight;
    private String physicalDimensions;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private String shippingClass;

    // Admin moderation audit information
    @Column(name = "moderation_reason", columnDefinition = "TEXT")
    private String moderationReason;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private java.time.Instant moderatedAt;
}
