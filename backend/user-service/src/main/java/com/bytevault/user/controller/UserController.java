package com.bytevault.user.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.user.dto.UpdateProfileRequest;
import com.bytevault.user.dto.UserProfileResponse;
import com.bytevault.user.service.UserProfileService;
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
public class UserController {

    private final UserProfileService userProfileService;

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
    public ResponseEntity<ApiResponse<java.util.List<com.bytevault.user.entity.UserAddress>>> getMyAddresses(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        UUID userId = resolveUserId(headerUserId, authentication);
        return ResponseEntity.ok(ApiResponse.success("Addresses retrieved", userProfileService.getUserAddresses(userId)));
    }

    @PostMapping("/me/addresses")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.bytevault.user.entity.UserAddress>> addAddress(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody com.bytevault.user.entity.UserAddress address,
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
                // Fallback deterministic UUID from username
                return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
            }
        }
        throw new IllegalArgumentException("User identity could not be resolved from authentication context");
    }
}
