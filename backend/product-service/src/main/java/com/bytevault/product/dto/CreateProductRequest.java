package com.bytevault.product.dto;

import com.bytevault.product.entity.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Product price is required")
    @Positive(message = "Product price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Product type is required")
    private ProductType productType;

    private Long categoryId;

    // Digital configs
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileVersion;

    // Physical fields
    private String physicalSku;
    private Double physicalWeight;
    private String physicalDimensions;
}
