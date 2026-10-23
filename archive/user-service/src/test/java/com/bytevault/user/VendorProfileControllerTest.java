package com.bytevault.user;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.user.controller.VendorProfileController;
import com.bytevault.user.dto.UpdateVendorProfileRequest;
import com.bytevault.user.dto.VendorProfileResponse;
import com.bytevault.user.dto.VendorStatusSummaryResponse;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VendorProfileControllerTest {

    @Mock
    private VendorProfileService vendorProfileService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private VendorProfileController vendorProfileController;

    @Test
    @DisplayName("Vendor get own profile returns profile response")
    void testGetMyVendorProfile() {
        UUID vendorId = UUID.randomUUID();
        VendorProfileResponse profile = VendorProfileResponse.builder()
                .id(UUID.randomUUID())
                .userId(vendorId)
                .storeName("My Craft Store")
                .storeSlug("my-craft-store")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileService.getVendorProfileByUserId(vendorId)).thenReturn(profile);

        ResponseEntity<ApiResponse<VendorProfileResponse>> res = vendorProfileController.getMyVendorProfile(
                vendorId.toString(), authentication);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().success());
        assertEquals("My Craft Store", res.getBody().data().getStoreName());
        assertEquals(VendorStatus.PENDING_APPROVAL, res.getBody().data().getStatus());
    }

    @Test
    @DisplayName("Vendor update own profile returns updated response")
    void testUpdateMyVendorProfile() {
        UUID vendorId = UUID.randomUUID();
        UpdateVendorProfileRequest req = UpdateVendorProfileRequest.builder()
                .storeName("Updated Store")
                .storeDescription("New Description")
                .build();

        VendorProfileResponse updated = VendorProfileResponse.builder()
                .id(UUID.randomUUID())
                .userId(vendorId)
                .storeName("Updated Store")
                .storeDescription("New Description")
                .status(VendorStatus.APPROVED)
                .build();

        when(vendorProfileService.updateVendorProfile(eq(vendorId), any(UpdateVendorProfileRequest.class)))
                .thenReturn(updated);

        ResponseEntity<ApiResponse<VendorProfileResponse>> res = vendorProfileController.updateMyVendorProfile(
                req, vendorId.toString(), authentication);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("Updated Store", res.getBody().data().getStoreName());
    }

    @Test
    @DisplayName("Internal vendor status endpoint returns status summary")
    void testGetVendorStatusInternal() {
        UUID vendorId = UUID.randomUUID();
        VendorStatusSummaryResponse summary = VendorStatusSummaryResponse.builder()
                .userId(vendorId)
                .storeName("ProStore")
                .status(VendorStatus.APPROVED)
                .isApproved(true)
                .build();

        when(vendorProfileService.getVendorStatusSummary(vendorId)).thenReturn(summary);

        ResponseEntity<VendorStatusSummaryResponse> res = vendorProfileController.getVendorStatusInternal(vendorId);

        assertNotNull(res);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertTrue(res.getBody().isApproved());
        assertEquals(VendorStatus.APPROVED, res.getBody().getStatus());
    }
}
