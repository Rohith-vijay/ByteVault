package com.marketplace.order.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderRequest {
    private BigDecimal amount;
    private String currency;
    private String receipt;
}
