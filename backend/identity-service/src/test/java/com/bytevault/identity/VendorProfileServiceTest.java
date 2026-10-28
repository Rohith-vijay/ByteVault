package com.bytevault.identity;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.identity.dto.UpdateVendorProfileRequest;
import com.bytevault.identity.dto.VendorProfileResponse;
import com.bytevault.identity.dto.VendorStatusSummaryResponse;
import com.bytevault.identity.entity.VendorProfile;
import com.bytevault.identity.entity.VendorStatus;
import com.bytevault.identity.repository.VendorProfileRepository;
import com.bytevault.identity.service.VendorProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorProfileServiceTest {

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @InjectMocks
    private VendorProfileService vendorProfileService;

    private UUID vendorUserId;
    private UUID vendorProfileId;
    private VendorProfile pendingProfile;

    @BeforeEach
    void setUp() {
        vendorUserId = UUID.randomUUID();
        vendorProfileId = UUID.randomUUID();
        pendingProfile = VendorProfile.builder()
                .id(vendorProfileId)
                .userId(vendorUserId)
                .email("vendor@bytevault.com")
                .storeName("Apex Digital")
                .storeSlug("apex-digital")
                .status(VendorStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Create Pending Vendor Profile: sets status to PENDING and generates slug")
    void testCreatePendingVendorProfile() {
        when(vendorProfileRepository.findByUserId(vendorUserId)).thenReturn(Optional.empty());
        when(vendorProfileRepository.existsByStoreSlug(anyString())).thenReturn(false);
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfile created = vendorProfileService.createPendingVendorProfile(
                vendorUserId,
                "vendor@bytevault.com",
                "Apex Digital",
                "3D assets",
                "TAX-1",
                "Bank Info",
                "support@apex.com"
        );

        assertNotNull(created);
        assertEquals(VendorStatus.PENDING, created.getStatus());
        assertEquals("apex-digital", created.getStoreSlug());
        assertEquals("Apex Digital", created.getStoreName());
    }

    @Test
    @DisplayName("Approve Vendor: transitions PENDING to APPROVED")
    void testApproveVendor() {
        UUID adminId = UUID.randomUUID();
        when(vendorProfileRepository.findById(vendorProfileId)).thenReturn(Optional.of(pendingProfile));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.approveVendor(vendorProfileId, adminId);

        assertNotNull(response);
        assertEquals(VendorStatus.APPROVED, response.getStatus());
        assertEquals(adminId, response.getReviewedBy());
        assertNotNull(response.getReviewedAt());
    }

    @Test
    @DisplayName("Suspend Vendor: transitions APPROVED to SUSPENDED")
    void testSuspendVendor() {
        UUID adminId = UUID.randomUUID();
        pendingProfile.setStatus(VendorStatus.APPROVED);

        when(vendorProfileRepository.findById(vendorProfileId)).thenReturn(Optional.of(pendingProfile));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.suspendVendor(vendorProfileId, adminId, "Copyright infringement");

        assertNotNull(response);
        assertEquals(VendorStatus.SUSPENDED, response.getStatus());
        assertEquals("Copyright infringement", response.getSuspensionReason());
    }

    @Test
    @DisplayName("Reactivate Vendor: transitions SUSPENDED to APPROVED")
    void testReactivateVendor() {
        UUID adminId = UUID.randomUUID();
        pendingProfile.setStatus(VendorStatus.SUSPENDED);

        when(vendorProfileRepository.findById(vendorProfileId)).thenReturn(Optional.of(pendingProfile));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.reactivateVendor(vendorProfileId, adminId);

        assertNotNull(response);
        assertEquals(VendorStatus.APPROVED, response.getStatus());
        assertNull(response.getSuspensionReason());
    }

    @Test
    @DisplayName("Illegal transition: cannot reactivate an already approved vendor")
    void testIllegalTransition_ThrowsBadRequestException() {
        UUID adminId = UUID.randomUUID();
        pendingProfile.setStatus(VendorStatus.APPROVED);

        when(vendorProfileRepository.findById(vendorProfileId)).thenReturn(Optional.of(pendingProfile));

        assertThrows(BadRequestException.class, () -> vendorProfileService.reactivateVendor(vendorProfileId, adminId));
    }

    @Test
    @DisplayName("Vendor Status Summary: returns correct status and approved boolean")
    void testGetVendorStatusSummary() {
        pendingProfile.setStatus(VendorStatus.APPROVED);
        when(vendorProfileRepository.findByUserId(vendorUserId)).thenReturn(Optional.of(pendingProfile));

        VendorStatusSummaryResponse summary = vendorProfileService.getVendorStatusSummary(vendorUserId);

        assertNotNull(summary);
        assertTrue(summary.isApproved());
        assertEquals("APPROVED", summary.getStatus());
    }
}
