package com.bytevault.payment.dto;

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
public class VendorEarningsResponse {
    private UUID vendorId;
    private BigDecimal grossEarnings;
    private BigDecimal platformFees;
    private BigDecimal netEarnings;
    private BigDecimal availableBalance;
    private BigDecimal pendingSettlement;
    private int totalOrdersCount;

    @Builder.Default
    private List<VendorLedgerItemDto> ledgerEntries = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorLedgerItemDto {
        private UUID id;
        private UUID orderId;
        private BigDecimal grossAmount;
        private BigDecimal platformFee;
        private BigDecimal netAmount;
        private String status;
        private java.time.LocalDateTime createdAt;
    }
}
