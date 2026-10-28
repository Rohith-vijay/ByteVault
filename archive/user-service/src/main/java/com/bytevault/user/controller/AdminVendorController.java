package com.bytevault.user.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.user.dto.VendorProfileResponse;
import com.bytevault.user.dto.VendorStatusActionRequest;
import com.bytevault.user.entity.VendorStatus;
import com.bytevault.user.service.VendorProfileService;
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
@RequestMapping({"/api/v1/users/admin/vendors", "/api/v1/admin/vendors"})
@RequiredArgsConstructor
public class AdminVendorController {

    private final VendorProfileService vendorProfileService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<VendorProfileResponse>>> getPendingVendors() {
        log.info("[AdminVendorController] Fetching pending vendor applications");
        List<VendorProfileResponse> pending = vendorProfileService.getPendingVendors();
        return ResponseEntity.ok(ApiResponse.success("Pending vendor applications retrieved", pending));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<VendorProfileResponse>>> getAllVendors(
            @RequestParam(value = "status", required = false) VendorStatus status) {
        log.info("[AdminVendorController] Fetching all vendors (filter={})", status);
        List<VendorProfileResponse> vendors = vendorProfileService.getAllVendors(status);
        return ResponseEntity.ok(ApiResponse.success("Vendors retrieved", vendors));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> getVendorById(@PathVariable UUID id) {
        log.info("[AdminVendorController] Fetching vendor details: id={}", id);
        VendorProfileResponse vendor = vendorProfileService.getVendorById(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor details retrieved", vendor));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> approveVendor(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String adminHeaderId,
            Authentication auth) {
        UUID adminId = resolveAdminId(adminHeaderId, auth);
        log.info("[AdminVendorController] Admin {} approving vendor: id={}", adminId, id);
        VendorProfileResponse response = vendorProfileService.approveVendor(id, adminId);
        return ResponseEntity.ok(ApiResponse.success("Vendor approved successfully", response));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> rejectVendor(
            @PathVariable UUID id,
            @RequestBody(required = false) VendorStatusActionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String adminHeaderId,
            Authentication auth) {
        UUID adminId = resolveAdminId(adminHeaderId, auth);
        String reason = (request != null) ? request.getReason() : null;
        log.info("[AdminVendorController] Admin {} rejecting vendor: id={}, reason={}", adminId, id, reason);
        VendorProfileResponse response = vendorProfileService.rejectVendor(id, adminId, reason);
        return ResponseEntity.ok(ApiResponse.success("Vendor application rejected", response));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> suspendVendor(
            @PathVariable UUID id,
            @RequestBody(required = false) VendorStatusActionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String adminHeaderId,
            Authentication auth) {
        UUID adminId = resolveAdminId(adminHeaderId, auth);
        String reason = (request != null) ? request.getReason() : null;
        log.info("[AdminVendorController] Admin {} suspending vendor: id={}, reason={}", adminId, id, reason);
        VendorProfileResponse response = vendorProfileService.suspendVendor(id, adminId, reason);
        return ResponseEntity.ok(ApiResponse.success("Vendor suspended successfully", response));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VendorProfileResponse>> reactivateVendor(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String adminHeaderId,
            Authentication auth) {
        UUID adminId = resolveAdminId(adminHeaderId, auth);
        log.info("[AdminVendorController] Admin {} reactivating vendor: id={}", adminId, id);
        VendorProfileResponse response = vendorProfileService.reactivateVendor(id, adminId);
        return ResponseEntity.ok(ApiResponse.success("Vendor reactivated successfully", response));
    }

    private UUID resolveAdminId(String headerUserId, Authentication authentication) {
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
        return UUID.randomUUID();
    }
}
