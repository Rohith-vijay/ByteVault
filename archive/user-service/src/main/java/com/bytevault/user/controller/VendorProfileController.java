package com.bytevault.user.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.user.dto.UpdateVendorProfileRequest;
import com.bytevault.user.dto.VendorProfileResponse;
import com.bytevault.user.dto.VendorStatusSummaryResponse;
import com.bytevault.user.service.VendorProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class VendorProfileController {

    private final VendorProfileService vendorProfileService;

    @GetMapping("/me/vendor-profile")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> getMyVendorProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication auth) {
        UUID vendorId = resolveUserId(headerUserId, auth);
        log.info("[VendorProfileController] Fetching vendor profile for vendorId={}", vendorId);
        VendorProfileResponse profile = vendorProfileService.getVendorProfileByUserId(vendorId);
        return ResponseEntity.ok(ApiResponse.success("Vendor profile retrieved", profile));
    }

    @PutMapping("/me/vendor-profile")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> updateMyVendorProfile(
            @Valid @RequestBody UpdateVendorProfileRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication auth) {
        UUID vendorId = resolveUserId(headerUserId, auth);
        log.info("[VendorProfileController] Updating vendor profile for vendorId={}", vendorId);
        VendorProfileResponse profile = vendorProfileService.updateVendorProfile(vendorId, request);
        return ResponseEntity.ok(ApiResponse.success("Vendor profile updated successfully", profile));
    }

    @GetMapping("/internal/vendors/{userId}/status")
    public ResponseEntity<VendorStatusSummaryResponse> getVendorStatusInternal(@PathVariable UUID userId) {
        return ResponseEntity.ok(vendorProfileService.getVendorStatusSummary(userId));
    }

    @GetMapping("/stores/{slug}")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> getStoreBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success("Store retrieved", vendorProfileService.getVendorBySlug(slug)));
    }

    private UUID resolveUserId(String headerUserId, Authentication authentication) {
        if (headerUserId != null && !headerUserId.trim().isEmpty()) {
            try {
                return UUID.fromString(headerUserId.trim());
            } catch (Exception ignored) {}
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return UUID.fromString(authentication.getName().trim());
            } catch (Exception ignored) {
                return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
            }
        }
        throw new IllegalArgumentException("User identity could not be resolved from authentication context");
    }
}
