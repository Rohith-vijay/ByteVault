package com.bytevault.user.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVendorProfileRequest {

    @Size(max = 100, message = "Store name must be under 100 characters")
    private String storeName;

    @Size(max = 2000, message = "Store description must be under 2000 characters")
    private String storeDescription;

    private String logoUrl;
    private String supportEmail;
    private String businessTaxId;
    private String payoutInfo;
}
