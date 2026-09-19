package com.bytevault.order.dto;

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
public class OrderItemResponse {
    private UUID id;
    private UUID productId;
    private UUID vendorId;
    private String productName;
    private String sku;
    private String productType;
    private BigDecimal unitPrice;
    private Integer quantity;
    private Boolean isDigital;
    private BigDecimal subtotal;
}
