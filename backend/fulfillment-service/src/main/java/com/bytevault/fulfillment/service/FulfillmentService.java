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

        // Idempotency: Check if order already has an existing fulfillment record
        if (fulfillmentRepository.findByOrderId(orderId).isPresent()) {
            log.info("[FulfillmentService] Order {} already fulfilled. Ensuring missing entitlements if any.", orderId);
        } else {
            try {
                Fulfillment fulfillment = Fulfillment.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .status("FULFILLED")
                        .fulfilledAt(LocalDateTime.now())
                        .build();
                fulfillmentRepository.save(fulfillment);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.info("[FulfillmentService] Concurrent fulfillment creation detected for orderId={}, proceeding idempotently", orderId);
            }
        }

        for (UUID prodId : productIds) {
            // Check if entitlement for this specific order and product already exists
            if (entitlementRepository.existsByOrderIdAndProductId(orderId, prodId)) {
                log.info("[FulfillmentService] Entitlement already exists for orderId={} and productId={}, skipping", orderId, prodId);
                continue;
            }

            try {
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
                log.info("[FulfillmentService] Entitlement GRANTED for user={} to product={} (order={})", userId, prodId, orderId);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                log.info("[FulfillmentService] Entitlement already created concurrently for orderId={} and productId={}", orderId, prodId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Entitlement> getMyEntitlements(UUID userId) {
        return entitlementRepository.findByUserIdOrderByGrantedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<com.bytevault.fulfillment.dto.EntitlementResponse> getMyEnrichedEntitlements(UUID userId) {
        List<Entitlement> entitlements = entitlementRepository.findByUserIdOrderByGrantedAtDesc(userId);
        List<com.bytevault.fulfillment.dto.EntitlementResponse> results = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Entitlement e : entitlements) {
            String title = "Digital Blueprint";
            String fileName = "asset.zip";
            String fileSize = "14.2 MB";
            String version = "v1.0";
            String format = "ZIP";

            try {
                if (productClient != null) {
                    org.springframework.http.ResponseEntity<String> res = productClient.getProductById(e.getProductId());
                    if (res != null && res.getBody() != null) {
                        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(res.getBody());
                        com.fasterxml.jackson.databind.JsonNode data = root.has("data") ? root.get("data") : root;
                        if (data.has("name") && !data.get("name").isNull()) {
                            title = data.get("name").asText();
                        }
                        if (data.has("fileName") && !data.get("fileName").isNull()) {
                            fileName = data.get("fileName").asText();
                        }
                        if (data.has("fileVersion") && !data.get("fileVersion").isNull()) {
                            version = data.get("fileVersion").asText();
                        }
                        if (data.has("fileSize") && !data.get("fileSize").isNull()) {
                            long bytes = data.get("fileSize").asLong();
                            if (bytes > 1024 * 1024) {
                                fileSize = String.format("%.1f MB", bytes / (1024.0 * 1024.0));
                            } else if (bytes > 1024) {
                                fileSize = String.format("%.1f KB", bytes / 1024.0);
                            }
                        }
                        if (fileName.toLowerCase().endsWith(".pdf")) {
                            format = "PDF";
                        } else if (fileName.toLowerCase().endsWith(".zip")) {
                            format = "ZIP";
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[FulfillmentService] Could not fetch product details for productId={}: {}", e.getProductId(), ex.getMessage());
            }

            String purchaseDate = e.getGrantedAt() != null
                    ? e.getGrantedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
                    : "Recent";

            String licenseKey = "BV-" + Math.abs(e.getId().getMostSignificantBits() % 10000)
                    + "-" + Math.abs(e.getId().getLeastSignificantBits() % 10000);

            results.add(com.bytevault.fulfillment.dto.EntitlementResponse.builder()
                    .id(e.getId())
                    .userId(e.getUserId())
                    .productId(e.getProductId())
                    .orderId(e.getOrderId())
                    .title(title)
                    .productName(title)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .version(version)
                    .format(format)
                    .licenseKey(licenseKey)
                    .status(e.getStatus())
                    .grantedAt(e.getGrantedAt())
                    .purchaseDate(purchaseDate)
                    .downloadCount(e.getDownloadCount())
                    .expiresAt(e.getExpiresAt())
                    .build());
        }
        return results;
    }

    @Transactional
    public Entitlement revokeEntitlement(UUID entitlementId) {
        Entitlement entitlement = entitlementRepository.findById(entitlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found with id: " + entitlementId));

        entitlement.setStatus("REVOKED");
        Entitlement saved = entitlementRepository.save(entitlement);
        log.info("[FulfillmentService] Entitlement REVOKED: id={}, user={}, product={}",
                entitlementId, entitlement.getUserId(), entitlement.getProductId());
        return saved;
    }

    @Transactional
    public String requestSecureDownload(UUID userId, UUID productId, String ipAddress, String userAgent) {
        log.info("[FulfillmentService] Secure download request: user={}, product={}", userId, productId);

        List<Entitlement> entitlements = entitlementRepository.findByUserIdAndProductIdOrderByGrantedAtDesc(userId, productId);

        if (entitlements.isEmpty()) {
            log.warn("[FulfillmentService] Download DENIED: No entitlement found for user={} and product={}", userId, productId);
            logDownload(userId, productId, null, ipAddress, userAgent, "DENIED");
            throw new IllegalArgumentException("No active entitlement found for this product.");
        }

        // Find the newest ACTIVE non-expired entitlement (supports repurchase and renewed entitlements)
        LocalDateTime now = LocalDateTime.now();
        Entitlement activeEntitlement = entitlements.stream()
                .filter(e -> "ACTIVE".equalsIgnoreCase(e.getStatus()))
                .filter(e -> e.getExpiresAt() == null || e.getExpiresAt().isAfter(now))
                .findFirst()
                .orElse(null);

        if (activeEntitlement == null) {
            Entitlement latest = entitlements.get(0);
            if ("REVOKED".equalsIgnoreCase(latest.getStatus())) {
                log.warn("[FulfillmentService] Download DENIED: Latest entitlement REVOKED for user={} and product={}", userId, productId);
                logDownload(userId, productId, latest.getId(), ipAddress, userAgent, "REVOKED");
                throw new IllegalArgumentException("This entitlement has been revoked.");
            }

            if (latest.getExpiresAt() != null && latest.getExpiresAt().isBefore(now)) {
                log.warn("[FulfillmentService] Download DENIED: Latest entitlement expired for user={} and product={}", userId, productId);
                latest.setStatus("EXPIRED");
                entitlementRepository.save(latest);
                logDownload(userId, productId, latest.getId(), ipAddress, userAgent, "EXPIRED");
                throw new IllegalArgumentException("Your download entitlement has expired.");
            }

            log.warn("[FulfillmentService] Download DENIED: No valid active entitlement for user={} and product={}", userId, productId);
            logDownload(userId, productId, latest.getId(), ipAddress, userAgent, "DENIED");
            throw new IllegalArgumentException("No active entitlement found for this product.");
        }

        Entitlement entitlement = activeEntitlement;

        try {
            String signedUrl = productClient.getDownloadUrlInternal(productId, gatewaySecret).getBody();
            if (signedUrl != null && signedUrl.contains("\"data\":")) {
                try {
                    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(signedUrl);
                    if (node.has("data") && node.get("data").isTextual()) {
                        signedUrl = node.get("data").asText();
                    }
                } catch (Exception ignored) {}
            }
            
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
