package com.bytevault.identity.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.identity.dto.*;
import com.bytevault.identity.entity.UserAddress;
import com.bytevault.identity.service.UserProfileService;
import com.bytevault.identity.service.VendorProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final VendorProfileService vendorProfileService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(headerUserId, authentication);
        UserProfileResponse profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", profile));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        UUID userId = resolveUserId(headerUserId, authentication);
        UserProfileResponse profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", profile));
    }

    @GetMapping("/me/addresses")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserAddress>>> getMyAddresses(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(headerUserId, authentication);
        return ResponseEntity.ok(ApiResponse.success("Addresses retrieved", userProfileService.getUserAddresses(userId)));
    }

    @PostMapping("/me/addresses")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserAddress>> addAddress(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody UserAddress address,
            Authentication authentication) {
        UUID userId = resolveUserId(headerUserId, authentication);
        return ResponseEntity.ok(ApiResponse.success("Address added", userProfileService.addAddress(userId, address)));
    }

    @DeleteMapping("/me/addresses/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable("id") UUID addressId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(headerUserId, authentication);
        userProfileService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(ApiResponse.success("Address removed", null));
    }

    @GetMapping("/me/vendor-profile")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> getMyVendorProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication auth) {
        UUID vendorId = resolveUserId(headerUserId, auth);
        log.info("[UserController] Fetching vendor profile for vendorId={}", vendorId);
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
        log.info("[UserController] Updating vendor profile for vendorId={}", vendorId);
        VendorProfileResponse profile = vendorProfileService.updateVendorProfile(vendorId, request);
        return ResponseEntity.ok(ApiResponse.success("Vendor profile updated successfully", profile));
    }

    @GetMapping("/internal/vendors/{userId}/status")
    public ResponseEntity<ApiResponse<VendorStatusSummaryResponse>> getVendorStatusInternal(@PathVariable UUID userId) {
        log.info("[UserController] Inter-service vendor status query for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.success("Vendor status retrieved", vendorProfileService.getVendorStatusSummary(userId)));
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
