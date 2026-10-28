package com.bytevault.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-vendor gross subtotal for a single order.
 * Gross is item subtotals only; shipping/tax are NOT attributed to vendors in this phase.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorBreakdownEntry {
    private UUID vendorId;
    private BigDecimal grossSubtotal;
}
