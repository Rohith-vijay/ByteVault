package com.bytevault.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorSaleResponse {
    private UUID orderItemId;
    private UUID orderId;
    private String orderNumber;
    private UUID productId;
    private String productName;
    private String sku;
    private String productType;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
    private String orderStatus;
    private String customerEmail;
    private String customerName;
    private LocalDateTime createdAt;
}
