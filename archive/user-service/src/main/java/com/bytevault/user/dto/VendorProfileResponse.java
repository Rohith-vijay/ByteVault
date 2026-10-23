package com.bytevault.user.dto;

import com.bytevault.user.entity.VendorStatus;
import lombok.*;

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
