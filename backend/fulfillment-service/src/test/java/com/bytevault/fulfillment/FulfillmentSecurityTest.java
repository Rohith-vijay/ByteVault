package com.bytevault.fulfillment;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.fulfillment.client.ProductClient;
import com.bytevault.fulfillment.entity.DownloadRecord;
import com.bytevault.fulfillment.entity.Entitlement;
import com.bytevault.fulfillment.repository.DownloadRecordRepository;
import com.bytevault.fulfillment.repository.EntitlementRepository;
import com.bytevault.fulfillment.repository.FulfillmentRepository;
import com.bytevault.fulfillment.service.FulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FulfillmentSecurityTest {

    @Mock
    private FulfillmentRepository fulfillmentRepository;

    @Mock
    private EntitlementRepository entitlementRepository;

    @Mock
    private DownloadRecordRepository downloadRecordRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private FulfillmentService fulfillmentService;

    private UUID userA;
    private UUID userB;
    private UUID product101;
    private UUID product202;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        product101 = UUID.randomUUID();
        product202 = UUID.randomUUID();
    }

    @Test
    @DisplayName("User A owns Product 101 -> Entitlement verified and Presigned URL granted")
    void testUserA_canDownload_PurchasedProduct101() {
        Entitlement entitlement = Entitlement.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .productId(product101)
                .status("ACTIVE")
                .downloadCount(0)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(entitlementRepository.findByUserIdAndProductIdOrderByGrantedAtDesc(userA, product101))
                .thenReturn(List.of(entitlement));
        when(productClient.getDownloadUrlInternal(eq(product101), any()))
                .thenReturn(ResponseEntity.ok("https://minio.bytevault.internal/assets/ebook-101.pdf?token=valid_15min_sig"));

        String downloadUrl = fulfillmentService.requestSecureDownload(userA, product101, "127.0.0.1", "Mozilla/5.0");

        assertNotNull(downloadUrl);
        assertTrue(downloadUrl.contains("ebook-101.pdf"));
        assertEquals(1, entitlement.getDownloadCount());
        verify(downloadRecordRepository, times(1)).save(argThat(record -> "SUCCESS".equals(record.getStatus())));
    }

    @Test
    @DisplayName("User A attempts to download Product 202 (not purchased) -> 403 / Access Denied")
    void testUserA_cannotDownload_UnpurchasedProduct202() {
        when(entitlementRepository.findByUserIdAndProductIdOrderByGrantedAtDesc(userA, product202))
                .thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fulfillmentService.requestSecureDownload(userA, product202, "127.0.0.1", "Mozilla/5.0"));

        assertTrue(ex.getMessage().contains("No active entitlement found"));
        verify(productClient, never()).getDownloadUrlInternal(any(), any());
        verify(downloadRecordRepository, times(1)).save(argThat(record -> "DENIED".equals(record.getStatus())));
    }

    @Test
    @DisplayName("User B attempts to download User A's Product 101 -> 403 / Access Denied")
    void testUserB_cannotDownload_UserA_Product101() {
        when(entitlementRepository.findByUserIdAndProductIdOrderByGrantedAtDesc(userB, product101))
                .thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fulfillmentService.requestSecureDownload(userB, product101, "192.168.1.50", "Mozilla/5.0"));

        assertTrue(ex.getMessage().contains("No active entitlement found"));
        verify(productClient, never()).getDownloadUrlInternal(any(), any());
        verify(downloadRecordRepository, times(1)).save(argThat(record -> "DENIED".equals(record.getStatus())));
    }

    @Test
    @DisplayName("User A entitlement expired -> Access Denied and status updated to EXPIRED")
    void testUserA_expiredEntitlement_isDenied() {
        Entitlement expiredEntitlement = Entitlement.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .productId(product101)
                .status("ACTIVE")
                .downloadCount(5)
                .expiresAt(LocalDateTime.now().minusDays(1)) // Expired yesterday
                .build();

        when(entitlementRepository.findByUserIdAndProductIdOrderByGrantedAtDesc(userA, product101))
                .thenReturn(List.of(expiredEntitlement));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fulfillmentService.requestSecureDownload(userA, product101, "127.0.0.1", "Mozilla/5.0"));

        assertTrue(ex.getMessage().contains("expired"));
        assertEquals("EXPIRED", expiredEntitlement.getStatus());
        verify(downloadRecordRepository, times(1)).save(argThat(record -> "EXPIRED".equals(record.getStatus())));
    }

    @Test
    @DisplayName("User A entitlement revoked -> Access Denied")
    void testUserA_revokedEntitlement_isDenied() {
        Entitlement revokedEntitlement = Entitlement.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .productId(product101)
                .status("REVOKED")
                .downloadCount(1)
                .build();

        when(entitlementRepository.findByUserIdAndProductIdOrderByGrantedAtDesc(userA, product101))
                .thenReturn(List.of(revokedEntitlement));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fulfillmentService.requestSecureDownload(userA, product101, "127.0.0.1", "Mozilla/5.0"));

        assertTrue(ex.getMessage().contains("revoked"));
        verify(downloadRecordRepository, times(1)).save(argThat(record -> "REVOKED".equals(record.getStatus())));
    }

    @Test
    @DisplayName("Retrieve user's owned entitlements")
    void testGetMyEntitlements() {
        Entitlement e1 = Entitlement.builder().id(UUID.randomUUID()).userId(userA).productId(product101).status("ACTIVE").build();
        Entitlement e2 = Entitlement.builder().id(UUID.randomUUID()).userId(userA).productId(product202).status("ACTIVE").build();

        when(entitlementRepository.findByUserIdOrderByGrantedAtDesc(userA)).thenReturn(List.of(e1, e2));

        List<Entitlement> list = fulfillmentService.getMyEntitlements(userA);

        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("Admin revokes entitlement")
    void testRevokeEntitlement() {
        UUID entitlementId = UUID.randomUUID();
        Entitlement entitlement = Entitlement.builder()
                .id(entitlementId)
                .userId(userA)
                .productId(product101)
                .status("ACTIVE")
                .build();

        when(entitlementRepository.findById(entitlementId)).thenReturn(Optional.of(entitlement));
        when(entitlementRepository.save(any(Entitlement.class))).thenReturn(entitlement);

        Entitlement revoked = fulfillmentService.revokeEntitlement(entitlementId);

        assertEquals("REVOKED", revoked.getStatus());
        verify(entitlementRepository, times(1)).save(entitlement);
    }
}
