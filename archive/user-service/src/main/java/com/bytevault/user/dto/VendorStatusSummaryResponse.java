package com.bytevault.user.dto;

import com.bytevault.user.entity.VendorStatus;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorStatusSummaryResponse {
    private UUID userId;
    private String storeName;
    private String storeSlug;
    private VendorStatus status;
    private boolean isApproved;
    private String rejectionReason;
    private String suspensionReason;
}
