package com.bytevault.payment;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.dto.RefundRequest;
import com.bytevault.payment.dto.RefundResponse;
import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.entity.Refund;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import com.bytevault.payment.repository.RefundRepository;
import com.bytevault.payment.service.RefundService;
import com.bytevault.payment.service.VendorLedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private VendorLedgerService vendorLedgerService;

    @InjectMocks
    private RefundService refundService;

    @Test
    @DisplayName("Process refund with valid amount succeeds and updates transaction & order status")
    void testProcessRefund_Success() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal refundAmount = new BigDecimal("499.00");

        RefundRequest request = RefundRequest.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .amount(refundAmount)
                .reason("Defective digital key")
                .idempotencyKey("refund_key_101")
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(paymentId)
                .orderId(orderId)
                .amount(new BigDecimal("499.00"))
                .status("SUCCESS")
                .build();

        when(refundRepository.findByIdempotencyKey("refund_key_101")).thenReturn(Optional.empty());
        when(transactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(tx));
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> {
            Refund r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RefundResponse response = refundService.processRefund(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(refundAmount, response.getAmount());
        assertEquals("REFUNDED", tx.getStatus());

        verify(transactionRepository, times(1)).save(tx);
        verify(vendorLedgerService, times(1)).reverseTransactionForRefund(eq(orderId), eq(refundAmount), eq(true));
        verify(orderClient, times(1)).refundCancel(eq(orderId), any(), eq("ROLE_INTERNAL_SERVICE"));
    }

    @Test
    @DisplayName("Idempotent refund replay returns cached response without duplicate execution")
    void testProcessRefund_IdempotentReplay() {
        UUID orderId = UUID.randomUUID();
        Refund existingRefund = Refund.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .idempotencyKey("refund_key_duplicate")
                .build();

        RefundRequest request = RefundRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("199.00"))
                .idempotencyKey("refund_key_duplicate")
                .build();

        when(refundRepository.findByIdempotencyKey("refund_key_duplicate")).thenReturn(Optional.of(existingRefund));

        RefundResponse response = refundService.processRefund(request);

        assertNotNull(response);
        assertEquals(existingRefund.getId(), response.getRefundId());

        verify(refundRepository, never()).save(any(Refund.class));
        verify(vendorLedgerService, never()).reverseTransactionForRefund(any(), any(), anyBoolean());
        verify(orderClient, never()).refundCancel(any(), any(), any());
    }

    @Test
    @DisplayName("Refund amount exceeding original transaction amount throws BadRequestException")
    void testProcessRefund_ExceedsOriginalAmount_ThrowsException() {
        UUID orderId = UUID.randomUUID();
        RefundRequest request = RefundRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("999.00"))
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(new BigDecimal("500.00"))
                .status("SUCCESS")
                .build();

        when(transactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(tx));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                refundService.processRefund(request));

        assertTrue(ex.getMessage().contains("Cumulative refund amount"));
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    @DisplayName("Partial refund sets transaction status to PARTIALLY_REFUNDED without cancelling order")
    void testProcessRefund_PartialRefund_DoesNotCancelOrder() {
        UUID orderId = UUID.randomUUID();
        RefundRequest request = RefundRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("200.00"))
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(new BigDecimal("500.00"))
                .status("SUCCESS")
                .build();

        when(transactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(tx));
        when(refundRepository.findByOrderId(orderId)).thenReturn(java.util.List.of());
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> {
            Refund r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RefundResponse response = refundService.processRefund(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("PARTIALLY_REFUNDED", tx.getStatus());

        // Since it's a partial refund, order is NOT cancelled
        verify(orderClient, never()).refundCancel(any(), any(), any());
        verify(vendorLedgerService, times(1)).reverseTransactionForRefund(eq(orderId), eq(new BigDecimal("200.00")), eq(false));
    }

    @Test
    @DisplayName("Cumulative refunds exceeding transaction total throw BadRequestException")
    void testProcessRefund_CumulativeExceedsTotal() {
        UUID orderId = UUID.randomUUID();

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .amount(new BigDecimal("300.00"))
                .status("PARTIALLY_REFUNDED")
                .build();

        Refund existingRefund1 = Refund.builder()
                .amount(new BigDecimal("200.00"))
                .status("SUCCESS")
                .build();

        when(transactionRepository.findByOrderId(orderId)).thenReturn(Optional.of(tx));
        when(refundRepository.findByOrderId(orderId)).thenReturn(java.util.List.of(existingRefund1));

        // Attempting to refund 150 when 200 is already refunded (200 + 150 = 350 > 300)
        RefundRequest request = RefundRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("150.00"))
                .build();

        assertThrows(BadRequestException.class, () -> refundService.processRefund(request));
        verify(refundRepository, never()).save(any(Refund.class));
    }
}
