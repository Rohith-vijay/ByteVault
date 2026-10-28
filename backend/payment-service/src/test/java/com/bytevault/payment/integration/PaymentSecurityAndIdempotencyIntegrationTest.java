package com.bytevault.payment.integration;

import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.controller.PaymentController;
import com.bytevault.payment.dto.PaymentVerifyRequest;
import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import com.bytevault.payment.service.RazorpayClientService;
import com.bytevault.payment.service.RazorpayPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentSecurityAndIdempotencyIntegrationTest {

    @Mock
    private RazorpayClientService razorpayClientService;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private com.bytevault.payment.service.PaymentOrderSyncService paymentOrderSyncService;

    @Mock
    private com.bytevault.payment.client.OrderClient orderClient;

    @Mock
    private com.bytevault.payment.service.VendorLedgerService vendorLedgerService;

    @Mock
    private com.bytevault.payment.service.RefundService refundService;

    @InjectMocks
    private RazorpayPaymentService razorpayPaymentService;

    private PaymentController paymentController;
    private final String keySecret = "rzp_test_secret_key_123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(razorpayPaymentService, "keyId", "rzp_test_id");
        ReflectionTestUtils.setField(razorpayPaymentService, "keySecret", keySecret);
        paymentController = new PaymentController(razorpayPaymentService, vendorLedgerService, refundService, transactionRepository, paymentOrderSyncService, orderClient);
    }

    private String calculateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    @DisplayName("Payment verification with valid HMAC-SHA256 signature updates order and emits event")
    void testValidPaymentVerification() throws Exception {
        String orderId = "order_rzp_123";
        String paymentId = "pay_rzp_456";
        String signature = calculateHmac(orderId + "|" + paymentId, keySecret);

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .dbOrderId(UUID.randomUUID().toString())
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(signature)
                .customerEmail("buyer@bytevault.com")
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId(orderId)
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(tx));

        ResponseEntity<Map<String, Object>> response = paymentController.verifyPayment(null, null, null, request);

        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("SUCCESS", tx.getStatus());

        verify(paymentOrderSyncService, times(1)).syncPaymentToOrder(any());
    }

    @Test
    @DisplayName("Tampered signature fails HMAC validation, updates tx to FAILED, leaves order PENDING and retryable")
    void testTamperedSignaturePaymentVerification() throws Exception {
        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .dbOrderId(UUID.randomUUID().toString())
                .razorpayOrderId("order_rzp_123")
                .razorpayPaymentId("pay_rzp_456")
                .razorpaySignature("forged_signature_xyz")
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId("order_rzp_123")
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId("order_rzp_123"))
                .thenReturn(Optional.of(tx));

        assertThrows(IllegalArgumentException.class, () -> paymentController.verifyPayment(null, null, null, request));

        // Per spec: tx updated to FAILED, order sync not called, order stays PENDING (no markOrderFailed call)
        assertEquals("FAILED", tx.getStatus());
        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
        verify(transactionRepository, atLeastOnce()).save(tx);
    }

    @Test
    @DisplayName("Failed verify attempt followed by subsequent valid verify on same order succeeds")
    void testFailedVerify_FollowedBySuccessfulVerify_OnSameOrderSucceeds() throws Exception {
        String rzpOrderId = "order_retry_101";
        String paymentId = "pay_retry_202";
        UUID dbOrderId = UUID.randomUUID();
        String validSignature = calculateHmac(rzpOrderId + "|" + paymentId, keySecret);

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId(rzpOrderId)
                .orderId(dbOrderId)
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(tx));

        // Attempt 1: invalid signature
        PaymentVerifyRequest badReq = PaymentVerifyRequest.builder()
                .dbOrderId(dbOrderId.toString())
                .razorpayOrderId(rzpOrderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature("invalid_sig")
                .build();

        assertThrows(IllegalArgumentException.class, () -> paymentController.verifyPayment(null, null, null, badReq));
        assertEquals("FAILED", tx.getStatus());
        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());

        // Attempt 2: valid retry on same order
        PaymentVerifyRequest goodReq = PaymentVerifyRequest.builder()
                .dbOrderId(dbOrderId.toString())
                .razorpayOrderId(rzpOrderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(validSignature)
                .build();

        ResponseEntity<Map<String, Object>> res = paymentController.verifyPayment(null, null, null, goodReq);

        assertNotNull(res.getBody());
        assertEquals("SUCCESS", res.getBody().get("status"));
        assertEquals("SUCCESS", tx.getStatus());
        verify(paymentOrderSyncService, times(1)).syncPaymentToOrder(tx);
    }

    @Test
    @DisplayName("Non-owner user passing another user's userId in body is rejected with 403 AccessDeniedException")
    void testVerifyPayment_MismatchedBodyUserId_ThrowsAccessDenied() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .razorpayOrderId("order_auth_101")
                .razorpayPaymentId("pay_123")
                .razorpaySignature("sig_123")
                .userId(ownerId.toString())
                .build();

        // Attacker calls verify with their own X-User-Id header
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                paymentController.verifyPayment(attackerId.toString(), "ROLE_CUSTOMER", null, request));

        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
    }

    @Test
    @DisplayName("Non-owner user attempting to verify transaction owned by another user is rejected with 403 AccessDeniedException")
    void testVerifyPayment_TransactionOwnedByAnotherUser_ThrowsAccessDenied() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        String rzpOrderId = "order_auth_102";

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId(rzpOrderId)
                .userId(ownerId)
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(tx));

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .razorpayOrderId(rzpOrderId)
                .razorpayPaymentId("pay_123")
                .razorpaySignature("sig_123")
                .build();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                paymentController.verifyPayment(attackerId.toString(), "ROLE_CUSTOMER", null, request));

        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
    }

    @Test
    @DisplayName("Owner user can verify their own payment transaction successfully")
    void testVerifyPayment_Owner_CanVerifySuccessfully() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String rzpOrderId = "order_owner_101";
        String paymentId = "pay_owner_202";
        String signature = calculateHmac(rzpOrderId + "|" + paymentId, keySecret);

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId(rzpOrderId)
                .userId(ownerId)
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(tx));

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .razorpayOrderId(rzpOrderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(signature)
                .userId(ownerId.toString())
                .build();

        ResponseEntity<Map<String, Object>> response =
                paymentController.verifyPayment(ownerId.toString(), "ROLE_CUSTOMER", null, request);

        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("SUCCESS", tx.getStatus());
        verify(paymentOrderSyncService, times(1)).syncPaymentToOrder(tx);
    }

    @Test
    @DisplayName("Admin role can verify any user's payment transaction")
    void testVerifyPayment_Admin_CanVerifyAnyUser() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String rzpOrderId = "order_admin_101";
        String paymentId = "pay_admin_202";
        String signature = calculateHmac(rzpOrderId + "|" + paymentId, keySecret);

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId(rzpOrderId)
                .userId(ownerId)
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId(rzpOrderId)).thenReturn(Optional.of(tx));

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .razorpayOrderId(rzpOrderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(signature)
                .userId(ownerId.toString())
                .build();

        ResponseEntity<Map<String, Object>> response =
                paymentController.verifyPayment(adminId.toString(), "ROLE_ADMIN", null, request);

        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("SUCCESS", tx.getStatus());
        verify(paymentOrderSyncService, times(1)).syncPaymentToOrder(tx);
    }

    @Test
    @DisplayName("Duplicate payment verification request is idempotent and does not re-emit events")
    void testIdempotentDuplicatePaymentVerification() throws Exception {
        String orderId = "order_already_processed";
        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_123")
                .razorpaySignature("sig_123")
                .build();

        PaymentTransaction paidTx = PaymentTransaction.builder()
                .razorpayOrderId(orderId)
                .status("SUCCESS")
                .build();

        when(transactionRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(paidTx));

        ResponseEntity<Map<String, Object>> response = paymentController.verifyPayment(null, null, null, request);

        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("idempotent"));

        // Verify no duplicate Feign call or failure call
        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
    }

    @Test
    @DisplayName("Security: Non-admin caller attempting refund is rejected with 403 AccessDeniedException")
    void testProcessRefund_NonAdmin_Forbidden() {
        com.bytevault.payment.dto.RefundRequest request = com.bytevault.payment.dto.RefundRequest.builder()
                .paymentId(UUID.randomUUID())
                .amount(new java.math.BigDecimal("50.00"))
                .reason("Customer requested")
                .build();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                paymentController.processRefund("ROLE_CUSTOMER", null, request));
    }

    @Test
    @DisplayName("Security: Admin caller successfully authorizes refund processing")
    void testProcessRefund_Admin_Success() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        com.bytevault.payment.dto.RefundRequest request = com.bytevault.payment.dto.RefundRequest.builder()
                .paymentId(paymentId)
                .amount(new java.math.BigDecimal("50.00"))
                .reason("Admin authorized")
                .build();

        com.bytevault.payment.dto.RefundResponse mockResponse = com.bytevault.payment.dto.RefundResponse.builder()
                .refundId(refundId)
                .status("PROCESSED")
                .build();

        when(refundService.processRefund(request)).thenReturn(mockResponse);

        ResponseEntity<com.bytevault.payment.dto.RefundResponse> response =
                paymentController.processRefund("ROLE_ADMIN", null, request);

        assertNotNull(response.getBody());
        assertEquals("PROCESSED", response.getBody().getStatus());
        verify(refundService, times(1)).processRefund(request);
    }
}
