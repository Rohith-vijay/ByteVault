package com.bytevault.product.dto;

import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private UUID id;
    private UUID vendorId;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private String sku;
    private ProductType productType;
    private ProductStatus status;
    private Long categoryId;
    private String categoryName;
    private String tags;

    // Digital configs
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileVersion;

    // Physical fields
    private String physicalSku;
    private Double physicalWeight;
    private String physicalDimensions;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private String shippingClass;

    // Moderation fields
    private String moderationReason;
    private UUID moderatedBy;
    private java.time.Instant moderatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
