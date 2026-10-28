package com.bytevault.user;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.user.dto.UpdateVendorProfileRequest;
import com.bytevault.user.dto.VendorProfileResponse;
import com.bytevault.user.dto.VendorStatusSummaryResponse;
import com.bytevault.user.entity.VendorProfile;
import com.bytevault.user.entity.VendorStatus;
import com.bytevault.user.repository.UserProfileRepository;
import com.bytevault.user.repository.VendorProfileRepository;
import com.bytevault.user.service.VendorProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VendorProfileServiceTest {

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private VendorProfileService vendorProfileService;

    @Test
    @DisplayName("Create pending vendor profile with unique slug")
    void testCreatePendingVendorProfile() {
        UUID userId = UUID.randomUUID();
        when(vendorProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(vendorProfileRepository.existsByStoreSlug("pixel-craft")).thenReturn(false);
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> {
            VendorProfile p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        VendorProfile profile = vendorProfileService.createPendingVendorProfile(
                userId,
                "vendor@bytevault.com",
                "Pixel Craft",
                "Premium UI Kits & Digital Goods",
                "TAX-9988",
                "bank:123456",
                "support@pixelcraft.io"
        );

        assertNotNull(profile);
        assertEquals(userId, profile.getUserId());
        assertEquals("Pixel Craft", profile.getStoreName());
        assertEquals("pixel-craft", profile.getStoreSlug());
        assertEquals(VendorStatus.PENDING_APPROVAL, profile.getStatus());
        assertEquals("support@pixelcraft.io", profile.getSupportEmail());
    }

    @Test
    @DisplayName("Approve pending vendor successfully")
    void testApproveVendor_Success() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorProfile pending = VendorProfile.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("DevStudio")
                .storeSlug("devstudio")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileRepository.findById(vendorId)).thenReturn(Optional.of(pending));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.approveVendor(vendorId, adminId);

        assertNotNull(response);
        assertEquals(VendorStatus.APPROVED, response.getStatus());
        assertEquals(adminId, response.getReviewedBy());
        assertNotNull(response.getReviewedAt());
    }

    @Test
    @DisplayName("Reject pending vendor with reason")
    void testRejectVendor_Success() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorProfile pending = VendorProfile.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("ShadyStore")
                .storeSlug("shadystore")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileRepository.findById(vendorId)).thenReturn(Optional.of(pending));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.rejectVendor(vendorId, adminId, "Incomplete documentation");

        assertNotNull(response);
        assertEquals(VendorStatus.REJECTED, response.getStatus());
        assertEquals("Incomplete documentation", response.getRejectionReason());
        assertEquals(adminId, response.getReviewedBy());
    }

    @Test
    @DisplayName("Suspend approved vendor with reason")
    void testSuspendVendor_Success() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorProfile approved = VendorProfile.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("ActiveStore")
                .storeSlug("activestore")
                .status(VendorStatus.APPROVED)
                .build();

        when(vendorProfileRepository.findById(vendorId)).thenReturn(Optional.of(approved));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.suspendVendor(vendorId, adminId, "Policy violation");

        assertNotNull(response);
        assertEquals(VendorStatus.SUSPENDED, response.getStatus());
        assertEquals("Policy violation", response.getSuspensionReason());
        assertEquals(adminId, response.getReviewedBy());
    }

    @Test
    @DisplayName("Reactivate suspended vendor successfully")
    void testReactivateVendor_Success() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorProfile suspended = VendorProfile.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("SuspendedStore")
                .storeSlug("suspendedstore")
                .status(VendorStatus.SUSPENDED)
                .suspensionReason("Past violation")
                .build();

        when(vendorProfileRepository.findById(vendorId)).thenReturn(Optional.of(suspended));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        VendorProfileResponse response = vendorProfileService.reactivateVendor(vendorId, adminId);

        assertNotNull(response);
        assertEquals(VendorStatus.APPROVED, response.getStatus());
        assertNull(response.getSuspensionReason());
        assertEquals(adminId, response.getReviewedBy());
    }

    @Test
    @DisplayName("Invalid transition: Cannot suspend PENDING_APPROVAL vendor directly")
    void testInvalidTransition_PendingToSuspended() {
        UUID vendorId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        VendorProfile pending = VendorProfile.builder()
                .id(vendorId)
                .userId(UUID.randomUUID())
                .storeName("PendingStore")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileRepository.findById(vendorId)).thenReturn(Optional.of(pending));

        assertThrows(BadRequestException.class, () -> {
            vendorProfileService.suspendVendor(vendorId, adminId, "Invalid suspend");
        });
    }

    @Test
    @DisplayName("Update vendor profile preserves non-modified fields")
    void testUpdateVendorProfile() {
        UUID userId = UUID.randomUUID();
        VendorProfile existing = VendorProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .storeName("Original Name")
                .storeSlug("original-name")
                .storeDescription("Original Bio")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(vendorProfileRepository.save(any(VendorProfile.class))).thenAnswer(i -> i.getArgument(0));

        UpdateVendorProfileRequest updateReq = UpdateVendorProfileRequest.builder()
                .storeName("New Store Name")
                .storeDescription("Updated Bio")
                .supportEmail("newsupport@store.com")
                .build();

        VendorProfileResponse response = vendorProfileService.updateVendorProfile(userId, updateReq);

        assertNotNull(response);
        assertEquals("New Store Name", response.getStoreName());
        assertEquals("Updated Bio", response.getStoreDescription());
        assertEquals("newsupport@store.com", response.getSupportEmail());
    }

    @Test
    @DisplayName("Get vendor status summary returns correct isApproved boolean")
    void testGetVendorStatusSummary() {
        UUID userIdApproved = UUID.randomUUID();
        VendorProfile approved = VendorProfile.builder()
                .userId(userIdApproved)
                .storeName("ApprovedStore")
                .storeSlug("approvedstore")
                .status(VendorStatus.APPROVED)
                .build();

        UUID userIdPending = UUID.randomUUID();
        VendorProfile pending = VendorProfile.builder()
                .userId(userIdPending)
                .storeName("PendingStore")
                .storeSlug("pendingstore")
                .status(VendorStatus.PENDING_APPROVAL)
                .build();

        when(vendorProfileRepository.findByUserId(userIdApproved)).thenReturn(Optional.of(approved));
        when(vendorProfileRepository.findByUserId(userIdPending)).thenReturn(Optional.of(pending));

        VendorStatusSummaryResponse approvedSummary = vendorProfileService.getVendorStatusSummary(userIdApproved);
        assertTrue(approvedSummary.isApproved());
        assertEquals(VendorStatus.APPROVED, approvedSummary.getStatus());

        VendorStatusSummaryResponse pendingSummary = vendorProfileService.getVendorStatusSummary(userIdPending);
        assertFalse(pendingSummary.isApproved());
        assertEquals(VendorStatus.PENDING_APPROVAL, pendingSummary.getStatus());
    }
}
