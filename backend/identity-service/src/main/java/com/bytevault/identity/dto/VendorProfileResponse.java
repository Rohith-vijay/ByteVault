package com.bytevault.identity.dto;

import com.bytevault.identity.entity.VendorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorProfileResponse {
    private UUID id;
    private UUID userId;
    private String email;
    private String storeName;
    private String storeSlug;
    private String storeDescription;
    private String logoUrl;
    private String supportEmail;
    private String businessTaxId;
    private String payoutInfo;
    private VendorStatus status;
    private String rejectionReason;
    private String suspensionReason;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
