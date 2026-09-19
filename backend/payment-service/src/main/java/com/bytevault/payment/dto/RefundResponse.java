package com.bytevault.payment.dto;

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
public class RefundResponse {
    private UUID refundId;
    private UUID orderId;
    private UUID paymentId;
    private BigDecimal amount;
    private String reason;
    private String status;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
