package com.bytevault.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportRow {
    private int rowNumber;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private String sku;
    private String productType; // DIGITAL or PHYSICAL
    private String categoryName;
    private Long categoryId;
    private UUID vendorId;
    private Double weight;
    private Double length;
    private Double width;
    private Double height;

    @Builder.Default
    private boolean valid = true;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
