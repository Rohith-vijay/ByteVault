package com.bytevault.fulfillment.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.fulfillment.service.FulfillmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    @GetMapping("/downloads/{productId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> downloadDigitalProduct(
            @PathVariable UUID productId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication authentication,
            HttpServletRequest request) {

        UUID userId = resolveUserId(userIdHeader, authentication);
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        log.info("[FulfillmentController] Download requested by user={} for product={}", userId, productId);
        String downloadUrl = fulfillmentService.requestSecureDownload(userId, productId, ipAddress, userAgent);

        Map<String, String> response = new HashMap<>();
        response.put("downloadUrl", downloadUrl);
        return ResponseEntity.ok(ApiResponse.success("Secure presigned download URL generated", response));
    }

    @GetMapping({"/fulfillments/my-entitlements", "/entitlements"})
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<com.bytevault.fulfillment.dto.EntitlementResponse>>> getMyEntitlements(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication authentication) {

        UUID userId = resolveUserId(userIdHeader, authentication);
        return ResponseEntity.ok(ApiResponse.success("Entitlements retrieved", fulfillmentService.getMyEnrichedEntitlements(userId)));
    }

    @PostMapping("/fulfillments/entitlements/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.bytevault.fulfillment.entity.Entitlement>> revokeEntitlement(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Entitlement revoked", fulfillmentService.revokeEntitlement(id)));
    }

    @PostMapping({"/internal/fulfill", "/fulfillments/internal/fulfill"})
    public ResponseEntity<ApiResponse<Void>> fulfillOrderInternal(
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestParam("orderId") UUID orderId,
            @RequestParam("userId") UUID userId,
            @RequestBody java.util.List<UUID> productIds) {
        log.info("[FulfillmentController] Internal fulfillment execution requested for orderId={}, userId={}", orderId, userId);
        fulfillmentService.fulfillDigitalOrder(orderId, userId, productIds);
        return ResponseEntity.ok(ApiResponse.success("Order digitally fulfilled", null));
    }

    private UUID resolveUserId(String headerUserId, Authentication authentication) {
        if (headerUserId != null && !headerUserId.trim().isEmpty()) {
            try {
                return UUID.fromString(headerUserId);
            } catch (Exception ignored) {}
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (Exception ignored) {
                return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
            }
        }
        throw new IllegalArgumentException("User identity is required to authorize downloads.");
    }
}
