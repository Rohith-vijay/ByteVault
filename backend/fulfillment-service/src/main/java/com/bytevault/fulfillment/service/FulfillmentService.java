package com.bytevault.fulfillment.service;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.fulfillment.client.ProductClient;
import com.bytevault.fulfillment.entity.DownloadRecord;
import com.bytevault.fulfillment.entity.Entitlement;
import com.bytevault.fulfillment.entity.Fulfillment;
import com.bytevault.fulfillment.repository.DownloadRecordRepository;
import com.bytevault.fulfillment.repository.EntitlementRepository;
import com.bytevault.fulfillment.repository.FulfillmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final FulfillmentRepository fulfillmentRepository;
    private final EntitlementRepository entitlementRepository;
    private final DownloadRecordRepository downloadRecordRepository;
    private final ProductClient productClient;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Transactional
    public void fulfillDigitalOrder(UUID orderId, UUID userId, List<UUID> productIds) {
        log.info("[FulfillmentService] Launching fulfillment for orderId={} for userId={}, count={}", orderId, userId, productIds.size());
        
        Fulfillment fulfillment = Fulfillment.builder()
                .orderId(orderId)
                .userId(userId)
                .status("FULFILLED")
                .fulfilledAt(LocalDateTime.now())
                .build();
        
        fulfillmentRepository.save(fulfillment);

        for (UUID prodId : productIds) {
            Entitlement entitlement = Entitlement.builder()
                    .userId(userId)
                    .productId(prodId)
                    .orderId(orderId)
                    .grantedAt(LocalDateTime.now())
                    .status("ACTIVE")
                    .downloadCount(0)
                    .expiresAt(LocalDateTime.now().plusMonths(12)) // Default 1 year expiry
                    .build();
            
            entitlementRepository.save(entitlement);
            log.info("[FulfillmentService] Entitlement GRANTED for user={} to product={}", userId, prodId);
        }
    }

    @Transactional
    public String requestSecureDownload(UUID userId, UUID productId, String ipAddress, String userAgent) {
        log.info("[FulfillmentService] Secure download request: user={}, product={}", userId, productId);

        Entitlement entitlement = entitlementRepository.findByUserIdAndProductIdAndStatus(userId, productId, "ACTIVE")
                .orElse(null);

        if (entitlement == null) {
            log.warn("[FulfillmentService] Download DENIED: No active entitlement found for user={} and product={}", userId, productId);
            logDownload(userId, productId, null, ipAddress, userAgent, "DENIED");
            throw new IllegalArgumentException("No active entitlement found for this product.");
        }

        if (entitlement.getExpiresAt() != null && entitlement.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("[FulfillmentService] Download DENIED: Entitlement expired for user={} and product={}", userId, productId);
            entitlement.setStatus("EXPIRED");
            entitlementRepository.save(entitlement);
            logDownload(userId, productId, entitlement.getId(), ipAddress, userAgent, "EXPIRED");
            throw new IllegalArgumentException("Your download entitlement has expired.");
        }

        try {
            log.info("[FulfillmentService] Fetching signed URL from product-service using FeignClient for key/product: {}", productId);
            String signedUrl = productClient.getDownloadUrlInternal(productId, gatewaySecret).getBody();
            
            // Increment download count
            entitlement.setDownloadCount(entitlement.getDownloadCount() + 1);
            entitlementRepository.save(entitlement);

            logDownload(userId, productId, entitlement.getId(), ipAddress, userAgent, "SUCCESS");
            return signedUrl;
        } catch (Exception e) {
            log.error("[FulfillmentService] Error while calling product-service via FeignClient", e);
            logDownload(userId, productId, entitlement.getId(), ipAddress, userAgent, "FAILED");
            throw new RuntimeException("Could not generate download URL. Please try again later.");
        }
    }

    private void logDownload(UUID userId, UUID productId, UUID entitlementId, String ipAddress, String userAgent, String status) {
        DownloadRecord record = DownloadRecord.builder()
                .userId(userId)
                .productId(productId)
                .entitlementId(entitlementId)
                .timestamp(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status(status)
                .build();
        downloadRecordRepository.save(record);
    }
}
