package com.bytevault.product.security;

import com.bytevault.product.client.UserClient;
import com.bytevault.product.dto.VendorStatusSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorAuthorizationService {

    private final UserClient userClient;
    private final java.util.concurrent.ConcurrentHashMap<UUID, CachedVendorStatus> vendorStatusCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000L; // 60-second TTL to avoid N+1 Feign calls on catalog reads

    public record CachedVendorStatus(boolean approved, String status, long cachedAt) {}

    /**
     * Read-time gate: Checks whether a vendor is currently approved.
     * Platform/admin-created products (vendorId == null) are always considered approved.
     * Results are cached for 60 seconds to ensure high performance during catalog queries.
     */
    public boolean isVendorApproved(UUID vendorId) {
        if (vendorId == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        CachedVendorStatus cached = vendorStatusCache.get(vendorId);
        if (cached != null && (now - cached.cachedAt() < CACHE_TTL_MS)) {
            return cached.approved();
        }

        try {
            var response = userClient.getVendorStatus(vendorId);
            if (response != null && response.getBody() != null) {
                com.bytevault.common.api.ApiResponse<VendorStatusSummaryDto> apiResponse = response.getBody();
                VendorStatusSummaryDto status = apiResponse != null ? apiResponse.data() : null;
                if (status != null) {
                    boolean approved = status.isApproved() && "APPROVED".equalsIgnoreCase(status.getStatus());
                    vendorStatusCache.put(vendorId, new CachedVendorStatus(approved, status.getStatus(), now));
                    return approved;
                }
            }
        } catch (Exception e) {
            log.warn("[VendorAuthorizationService] Could not verify vendor status for vendorId={}: {}", vendorId, e.getMessage());
            if (cached != null) {
                return cached.approved();
            }
            return false;
        }

        vendorStatusCache.put(vendorId, new CachedVendorStatus(false, "NOT_FOUND", now));
        return false;
    }

    /**
     * Invalidate the cached vendor status (e.g. on status change or admin override).
     */
    public void invalidateCache(UUID vendorId) {
        if (vendorId != null) {
            vendorStatusCache.remove(vendorId);
        }
    }

    /**
     * Write-time gate: Asserts vendor is APPROVED for write/management operations.
     */
    public void assertVendorApproved(UUID vendorId, Authentication auth) {
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            log.debug("[VendorAuthorizationService] Admin bypass for vendorId={}", vendorId);
            return;
        }

        try {
            var response = userClient.getVendorStatus(vendorId);
            if (response != null && response.getBody() != null) {
                com.bytevault.common.api.ApiResponse<VendorStatusSummaryDto> apiResponse = response.getBody();
                VendorStatusSummaryDto status = apiResponse != null ? apiResponse.data() : null;
                if (status != null) {
                    boolean approved = status.isApproved() && "APPROVED".equalsIgnoreCase(status.getStatus());
                    vendorStatusCache.put(vendorId, new CachedVendorStatus(approved, status.getStatus(), System.currentTimeMillis()));
                    if (!approved) {
                        String currentStatus = (status.getStatus() != null) ? status.getStatus() : "PENDING_APPROVAL";
                        log.warn("[VendorAuthorizationService] Denying access to vendor {}: status={}", vendorId, currentStatus);
                        throw new AccessDeniedException(String.format(
                                "Access denied: Vendor account is currently '%s'. Seller operations require an APPROVED status from marketplace administration.",
                                currentStatus));
                    }
                    log.info("[VendorAuthorizationService] Vendor {} is APPROVED", vendorId);
                    return;
                }
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[VendorAuthorizationService] Could not verify vendor status via identity-service: {}", e.getMessage());
        }
    }
}
