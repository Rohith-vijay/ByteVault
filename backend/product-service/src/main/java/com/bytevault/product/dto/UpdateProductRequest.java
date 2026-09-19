package com.bytevault.product.dto;

import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    private ProductType productType;

    private ProductStatus status;

    private Long categoryId;

    private String tags;

    // Digital metadata
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileVersion;
    private String fileChecksum;

    // Physical metadata
    private String sku;
    private String currency;
    private String physicalSku;
    private Double physicalWeight;
    private String physicalDimensions;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;
    private String shippingClass;
}
