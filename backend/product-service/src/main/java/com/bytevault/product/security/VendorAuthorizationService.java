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
                    if (!status.isApproved() || !"APPROVED".equalsIgnoreCase(status.getStatus())) {
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
            log.warn("[VendorAuthorizationService] Could not verify vendor status via user-service: {}", e.getMessage());
            // If user-service explicitly returned 4xx/5xx or failed to connect in dev, log warning
        }
    }
}
