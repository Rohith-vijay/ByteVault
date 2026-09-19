package com.bytevault.product;

import com.bytevault.product.client.UserClient;
import com.bytevault.product.dto.VendorStatusSummaryDto;
import com.bytevault.product.security.VendorAuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VendorAuthorizationServiceTest {

    @Mock
    private UserClient userClient;

    @InjectMocks
    private VendorAuthorizationService vendorAuthorizationService;

    @Test
    @DisplayName("Admin role bypasses vendor approval check")
    void testAssertVendorApproved_AdminBypass() {
        UUID vendorId = UUID.randomUUID();
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertDoesNotThrow(() -> vendorAuthorizationService.assertVendorApproved(vendorId, adminAuth));
        verify(userClient, never()).getVendorStatus(any());
    }

    @Test
    @DisplayName("Approved vendor passes authorization check")
    void testAssertVendorApproved_ApprovedVendor_Passes() {
        UUID vendorId = UUID.randomUUID();
        Authentication vendorAuth = new UsernamePasswordAuthenticationToken(
                vendorId.toString(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));

        VendorStatusSummaryDto approvedDto = VendorStatusSummaryDto.builder()
                .userId(vendorId)
                .storeName("ProDev Store")
                .status("APPROVED")
                .isApproved(true)
                .build();

        when(userClient.getVendorStatus(vendorId)).thenReturn(ResponseEntity.ok(com.bytevault.common.api.ApiResponse.success("Success", approvedDto)));

        assertDoesNotThrow(() -> vendorAuthorizationService.assertVendorApproved(vendorId, vendorAuth));
    }

    @Test
    @DisplayName("Pending vendor is denied seller access with 403 AccessDeniedException")
    void testAssertVendorApproved_PendingVendor_Denied() {
        UUID vendorId = UUID.randomUUID();
        Authentication vendorAuth = new UsernamePasswordAuthenticationToken(
                vendorId.toString(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));

        VendorStatusSummaryDto pendingDto = VendorStatusSummaryDto.builder()
                .userId(vendorId)
                .storeName("Pending Store")
                .status("PENDING_APPROVAL")
                .isApproved(false)
                .build();

        when(userClient.getVendorStatus(vendorId)).thenReturn(ResponseEntity.ok(com.bytevault.common.api.ApiResponse.success("Success", pendingDto)));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            vendorAuthorizationService.assertVendorApproved(vendorId, vendorAuth);
        });

        assertTrue(ex.getMessage().contains("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("Suspended vendor is denied seller access with 403 AccessDeniedException")
    void testAssertVendorApproved_SuspendedVendor_Denied() {
        UUID vendorId = UUID.randomUUID();
        Authentication vendorAuth = new UsernamePasswordAuthenticationToken(
                vendorId.toString(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));

        VendorStatusSummaryDto suspendedDto = VendorStatusSummaryDto.builder()
                .userId(vendorId)
                .storeName("Suspended Store")
                .status("SUSPENDED")
                .isApproved(false)
                .build();

        when(userClient.getVendorStatus(vendorId)).thenReturn(ResponseEntity.ok(com.bytevault.common.api.ApiResponse.success("Success", suspendedDto)));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            vendorAuthorizationService.assertVendorApproved(vendorId, vendorAuth);
        });

        assertTrue(ex.getMessage().contains("SUSPENDED"));
    }

    @Test
    @DisplayName("Rejected vendor is denied seller access with 403 AccessDeniedException")
    void testAssertVendorApproved_RejectedVendor_Denied() {
        UUID vendorId = UUID.randomUUID();
        Authentication vendorAuth = new UsernamePasswordAuthenticationToken(
                vendorId.toString(), "pass", List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));

        VendorStatusSummaryDto rejectedDto = VendorStatusSummaryDto.builder()
                .userId(vendorId)
                .storeName("Rejected Store")
                .status("REJECTED")
                .isApproved(false)
                .build();

        when(userClient.getVendorStatus(vendorId)).thenReturn(ResponseEntity.ok(com.bytevault.common.api.ApiResponse.success("Success", rejectedDto)));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> {
            vendorAuthorizationService.assertVendorApproved(vendorId, vendorAuth);
        });

        assertTrue(ex.getMessage().contains("REJECTED"));
    }
}
