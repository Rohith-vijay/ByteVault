package com.bytevault.payment.service;

import com.bytevault.common.dto.OrderPaymentConfirmationRequest;
import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentOrderSyncAndReconcilerReliabilityTest {

    @Mock
    private OrderClient orderClient;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    private PaymentOrderSyncService syncService;
    private PaymentOrderReconciler reconciler;

    private final String gatewaySecret = "test_gateway_secret_secret=hidden_value";

    @BeforeEach
    void setUp() {
        syncService = new PaymentOrderSyncService(orderClient, transactionRepository);
        ReflectionTestUtils.setField(syncService, "gatewaySecret", gatewaySecret);
        reconciler = new PaymentOrderReconciler(transactionRepository, syncService);
    }

    @Test
    @DisplayName("Successful OrderClient handoff marks transaction as SYNCED")
    void testSyncPaymentToOrder_Success() {
        UUID orderId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .orderSyncStatus("PENDING")
                .syncAttempts(0)
                .build();

        boolean result = syncService.syncPaymentToOrder(tx);

        assertTrue(result);
        assertEquals("SYNCED", tx.getOrderSyncStatus());
        assertNull(tx.getLastSyncError());
        assertNull(tx.getNextSyncRetryAt());
        verify(transactionRepository, times(1)).save(tx);
    }

    @Test
    @DisplayName("Transient 503 Service Unavailable increments attempt count and schedules backoff")
    void testSyncPaymentToOrder_TransientFailure_SchedulesBackoff() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .orderSyncStatus("PENDING")
                .syncAttempts(0)
                .build();

        Request request = Request.create(Request.HttpMethod.POST, "/api/v1/orders/internal/" + orderId + "/mark-paid",
                Collections.emptyMap(), null, new RequestTemplate());
        doThrow(new FeignException.ServiceUnavailable("Service Unavailable", request, null, Collections.emptyMap()))
                .when(orderClient).markOrderPaid(any(), any(), any(), any());

        boolean result = syncService.syncPaymentToOrder(tx);

        assertFalse(result);
        assertEquals("PENDING", tx.getOrderSyncStatus());
        assertEquals(1, tx.getSyncAttempts());
        assertNotNull(tx.getNextSyncRetryAt());
        assertTrue(tx.getLastSyncError().contains("Transient error"));
        verify(transactionRepository, times(1)).save(tx);
    }

    @Test
    @DisplayName("Transient failure exceeding max attempts (5) transitions to FAILED state")
    void testSyncPaymentToOrder_MaxAttemptsExceeded_MarksFailed() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .orderSyncStatus("PENDING")
                .syncAttempts(4) // 5th attempt will exceed
                .build();

        doThrow(new RuntimeException("Connection timeout"))
                .when(orderClient).markOrderPaid(any(), any(), any(), any());

        boolean result = syncService.syncPaymentToOrder(tx);

        assertFalse(result);
        assertEquals("FAILED", tx.getOrderSyncStatus());
        assertEquals(5, tx.getSyncAttempts());
        assertNull(tx.getNextSyncRetryAt());
        assertTrue(tx.getLastSyncError().contains("Max sync attempts"));
    }

    @Test
    @DisplayName("Permanent 400 Bad Request (amount mismatch) immediately transitions to FAILED without retry")
    void testSyncPaymentToOrder_PermanentBadRequest_MarksFailedImmediately() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .orderSyncStatus("PENDING")
                .syncAttempts(0)
                .build();

        Request request = Request.create(Request.HttpMethod.POST, "/url", Collections.emptyMap(), null, new RequestTemplate());
        byte[] body = "{\"message\":\"Payment amount mismatch\"}".getBytes(StandardCharsets.UTF_8);
        doThrow(new FeignException.BadRequest("Bad Request", request, body, Collections.emptyMap()))
                .when(orderClient).markOrderPaid(any(), any(), any(), any());

        boolean result = syncService.syncPaymentToOrder(tx);

        assertFalse(result);
        assertEquals("FAILED", tx.getOrderSyncStatus());
        assertNull(tx.getNextSyncRetryAt());
        assertTrue(tx.getLastSyncError().contains("Permanent failure"));
    }

    @Test
    @DisplayName("Downstream response indicating order is already PAID is handled as idempotent success")
    void testSyncPaymentToOrder_AlreadyPaid_IdempotentSuccess() {
        UUID orderId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .orderSyncStatus("PENDING")
                .syncAttempts(1)
                .build();

        Request request = Request.create(Request.HttpMethod.POST, "/url", Collections.emptyMap(), null, new RequestTemplate());
        byte[] body = "{\"message\":\"Order is already marked as PAID\"}".getBytes(StandardCharsets.UTF_8);
        doThrow(new FeignException.BadRequest("Bad Request", request, body, Collections.emptyMap()))
                .when(orderClient).markOrderPaid(any(), any(), any(), any());

        boolean result = syncService.syncPaymentToOrder(tx);

        assertTrue(result);
        assertEquals("SYNCED", tx.getOrderSyncStatus());
        assertNull(tx.getLastSyncError());
        assertNull(tx.getNextSyncRetryAt());
    }

    @Test
    @DisplayName("Reconciler acquires lease before calling sync; concurrent instance loses lease and skips")
    void testReconciler_MultiWorkerLeaseSafety() {
        UUID txId1 = UUID.randomUUID();
        UUID txId2 = UUID.randomUUID();

        PaymentTransaction tx1 = PaymentTransaction.builder().id(txId1).orderId(UUID.randomUUID()).amount(BigDecimal.TEN).status("SUCCESS").orderSyncStatus("PENDING").build();
        PaymentTransaction tx2 = PaymentTransaction.builder().id(txId2).orderId(UUID.randomUUID()).amount(BigDecimal.TEN).status("SUCCESS").orderSyncStatus("PENDING").build();

        when(transactionRepository.findPendingOrderSyncs(any())).thenReturn(List.of(tx1, tx2));

        // tx1: lease acquired (1)
        when(transactionRepository.acquireReconciliationLease(eq(txId1), any(), any())).thenReturn(1);
        when(transactionRepository.findById(txId1)).thenReturn(Optional.of(tx1));

        // tx2: lease NOT acquired (0, claimed by concurrent worker)
        when(transactionRepository.acquireReconciliationLease(eq(txId2), any(), any())).thenReturn(0);

        reconciler.reconcilePendingSyncs();

        // Verify orderClient was called for tx1
        verify(orderClient, times(1)).markOrderPaid(eq(tx1.getOrderId()), any(), any(), any());
        // Verify orderClient was NOT called for tx2 because lease acquisition failed
        verify(orderClient, never()).markOrderPaid(eq(tx2.getOrderId()), any(), any(), any());
    }
}
