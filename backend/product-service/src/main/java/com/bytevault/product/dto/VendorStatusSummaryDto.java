package com.bytevault.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorStatusSummaryDto {
    private UUID userId;
    private String storeName;
    private String storeSlug;
    private String status;
    private boolean isApproved;
    private String rejectionReason;
    private String suspensionReason;
}
