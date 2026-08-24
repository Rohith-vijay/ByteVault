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
    private String name;
    private String description;
    private BigDecimal price;
    private ProductType productType;
    private ProductStatus status;
    private Long categoryId;
    private String categoryName;

    // Digital configs
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileVersion;

    // Physical fields
    private String physicalSku;
    private Double physicalWeight;
    private String physicalDimensions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
