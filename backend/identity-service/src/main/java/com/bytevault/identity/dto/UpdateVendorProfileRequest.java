package com.bytevault.identity.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVendorProfileRequest {
    @Size(max = 100)
    private String storeName;

    private String storeDescription;
    private String logoUrl;
    private String supportEmail;
    private String businessTaxId;
    private String payoutInfo;
}
