package com.bytevault.user;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.user.controller.AdminVendorController;
import com.bytevault.user.dto.VendorProfileResponse;
import com.bytevault.user.dto.VendorStatusActionRequest;
import com.bytevault.user.entity.VendorStatus;
import com.bytevault.user.service.VendorProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminVendorControllerTest {

    @Mock
    private VendorProfileService vendorProfileService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AdminVendorController adminVendorController;

    @Test
    @DisplayName("Admin get pending vendors returns pending list")
    void testGetPendingVendors() {
        VendorProfileResponse vendor = VendorProfileResponse.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .storeName("Studio A")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileService.getPendingVendors()).thenReturn(List.of(vendor));

        ResponseEntity<ApiResponse<List<VendorProfileResponse>>> res = adminVendorController.getPendingVendors();

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().success());
        assertEquals(1, res.getBody().data().size());
        assertEquals("Studio A", res.getBody().data().get(0).getStoreName());
    }

    @Test
    @DisplayName("Admin approve vendor returns approved profile")
    void testApproveVendor() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        VendorProfileResponse approved = VendorProfileResponse.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("Studio A")
                .status(VendorStatus.APPROVED)
                .reviewedBy(adminId)
                .reviewedAt(LocalDateTime.now())
                .build();

        when(vendorProfileService.approveVendor(eq(vendorId), any(UUID.class))).thenReturn(approved);

        ResponseEntity<ApiResponse<VendorProfileResponse>> res = adminVendorController.approveVendor(
                vendorId, adminId.toString(), authentication);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(VendorStatus.APPROVED, res.getBody().data().getStatus());
    }

    @Test
    @DisplayName("Admin reject vendor returns rejected profile")
    void testRejectVendor() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorStatusActionRequest action = VendorStatusActionRequest.builder()
                .reason("Low quality catalog sample")
                .build();

        VendorProfileResponse rejected = VendorProfileResponse.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("Studio A")
                .status(VendorStatus.REJECTED)
                .rejectionReason("Low quality catalog sample")
                .reviewedBy(adminId)
                .build();

        when(vendorProfileService.rejectVendor(eq(vendorId), any(UUID.class), eq("Low quality catalog sample"))).thenReturn(rejected);

        ResponseEntity<ApiResponse<VendorProfileResponse>> res = adminVendorController.rejectVendor(
                vendorId, action, adminId.toString(), authentication);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(VendorStatus.REJECTED, res.getBody().data().getStatus());
        assertEquals("Low quality catalog sample", res.getBody().data().getRejectionReason());
    }

    @Test
    @DisplayName("Admin suspend vendor returns suspended profile")
    void testSuspendVendor() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorStatusActionRequest action = VendorStatusActionRequest.builder()
                .reason("Copyright complaint received")
                .build();

        VendorProfileResponse suspended = VendorProfileResponse.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("Studio A")
                .status(VendorStatus.SUSPENDED)
                .suspensionReason("Copyright complaint received")
                .reviewedBy(adminId)
                .build();

        when(vendorProfileService.suspendVendor(eq(vendorId), any(UUID.class), eq("Copyright complaint received"))).thenReturn(suspended);

        ResponseEntity<ApiResponse<VendorProfileResponse>> res = adminVendorController.suspendVendor(
                vendorId, action, adminId.toString(), authentication);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(VendorStatus.SUSPENDED, res.getBody().data().getStatus());
        assertEquals("Copyright complaint received", res.getBody().data().getSuspensionReason());
    }

    @Test
    @DisplayName("Admin reactivate vendor returns approved profile")
    void testReactivateVendor() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        VendorProfileResponse reactivated = VendorProfileResponse.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("Studio A")
                .status(VendorStatus.APPROVED)
                .reviewedBy(adminId)
                .build();

        when(vendorProfileService.reactivateVendor(eq(vendorId), any(UUID.class))).thenReturn(reactivated);

        ResponseEntity<ApiResponse<VendorProfileResponse>> res = adminVendorController.reactivateVendor(
                vendorId, adminId.toString(), authentication);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(VendorStatus.APPROVED, res.getBody().data().getStatus());
    }
}

