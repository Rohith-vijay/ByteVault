package com.bytevault.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorStatusSummaryResponse {
    private UUID userId;
    private String status;
    private boolean approved;
    private String storeName;
    private String storeSlug;
}
