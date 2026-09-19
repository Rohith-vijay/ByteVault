package com.bytevault.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private String orderNumber;
    private UUID userId;
    private String status;
    private String orderType;
    private BigDecimal totalAmount;
    private String customerEmail;
    private String customerName;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;
}
