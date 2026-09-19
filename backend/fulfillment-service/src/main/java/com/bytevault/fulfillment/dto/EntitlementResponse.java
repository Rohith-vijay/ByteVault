package com.bytevault.fulfillment.dto;

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
public class EntitlementResponse {
    private UUID id;
    private UUID userId;
    private UUID productId;
    private UUID orderId;
    private String title;
    private String productName;
    private String fileName;
    private String fileSize;
    private String version;
    private String format;
    private String licenseKey;
    private String status;
    private LocalDateTime grantedAt;
    private String purchaseDate;
    private int downloadCount;
    private LocalDateTime expiresAt;
    private String downloadUrl;
}
