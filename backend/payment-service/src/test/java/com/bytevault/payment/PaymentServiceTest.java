package com.bytevault.payment;

import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.controller.PaymentController;
import com.bytevault.payment.dto.PaymentOrderRequest;
import com.bytevault.payment.dto.PaymentOrderResponse;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private RazorpayClientService razorpayClientService;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private com.bytevault.payment.service.PaymentOrderSyncService paymentOrderSyncService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private com.bytevault.payment.service.VendorLedgerService vendorLedgerService;

    @Mock
    private com.bytevault.payment.service.RefundService refundService;

    @InjectMocks
    private RazorpayPaymentService razorpayPaymentService;

    private PaymentController paymentController;
    private final String secret = "secret_key_test_123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(razorpayPaymentService, "keyId", "rzp_test_123");
        ReflectionTestUtils.setField(razorpayPaymentService, "keySecret", secret);
        paymentController = new PaymentController(razorpayPaymentService, vendorLedgerService, refundService, rabbitTemplate, transactionRepository, paymentOrderSyncService);
    }

    private String calculateHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
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
    @DisplayName("Payment verification with valid HMAC-SHA256 signature succeeds")
    void testVerifyPayment_ValidSignature() throws Exception {
        String orderId = "order_12345";
        String paymentId = "pay_67890";
        String validSignature = calculateHmacSha256(orderId + "|" + paymentId, secret);

        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .dbOrderId(UUID.randomUUID().toString())
                .razorpayOrderId(orderId)
                .razorpayPaymentId(paymentId)
                .razorpaySignature(validSignature)
                .userId(UUID.randomUUID().toString())
                .customerEmail("buyer@bytevault.com")
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .razorpayOrderId(orderId)
                .status("PENDING")
                .build();

        when(transactionRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(tx));

        ResponseEntity<Map<String, Object>> response = paymentController.verifyPayment(request);

        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("SUCCESS", tx.getStatus());
        verify(paymentOrderSyncService, times(1)).syncPaymentToOrder(any());
    }

    @Test
    @DisplayName("Payment verification with invalid signature -> Throws IllegalArgumentException and publishes failure event")
    void testVerifyPayment_InvalidSignature_ThrowsException() {
        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .dbOrderId(UUID.randomUUID().toString())
                .razorpayOrderId("order_12345")
                .razorpayPaymentId("pay_67890")
                .razorpaySignature("invalid_tampered_signature")
                .build();

        when(transactionRepository.findByRazorpayOrderId("order_12345"))
                .thenReturn(Optional.of(PaymentTransaction.builder().status("PENDING").build()));

        assertThrows(IllegalArgumentException.class, () -> paymentController.verifyPayment(request));
        verify(rabbitTemplate, times(1)).convertAndSend(eq("order.exchange"), eq("payment.failed"), any(Map.class));
    }

    @Test
    @DisplayName("Duplicate payment verification is idempotent")
    void testVerifyPayment_IdempotentDuplicate() throws Exception {
        String orderId = "order_already_paid";
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

        ResponseEntity<Map<String, Object>> response = paymentController.verifyPayment(request);

        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("idempotent"));
        // Ensure no duplicate events fired
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Map.class));
    }

    @Test
    @DisplayName("Razorpay webhook callback handles event and acknowledges")
    void testRazorpayWebhook() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
        ResponseEntity<Map<String, Object>> response = paymentController.handleRazorpayWebhook("valid_sig", payload);

        assertNotNull(response.getBody());
        assertEquals("PROCESSED", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Create payment order persists initial transaction with PENDING status")
    void testCreatePaymentOrder_PersistsPendingTransaction() throws Exception {
        PaymentOrderRequest request = PaymentOrderRequest.builder()
                .amount(new BigDecimal("299.00"))
                .currency("INR")
                .receipt(UUID.randomUUID().toString())
                .build();

        PaymentOrderResponse mockOrderResponse = PaymentOrderResponse.builder()
                .orderId("order_mock_12345")
                .amount(29900L)
                .currency("INR")
                .build();

        RazorpayPaymentService mockPaymentService = mock(RazorpayPaymentService.class);
        when(mockPaymentService.createOrder(any())).thenReturn(mockOrderResponse);

        PaymentController controllerWithMock = new PaymentController(
                mockPaymentService, vendorLedgerService, refundService, rabbitTemplate, transactionRepository, paymentOrderSyncService);

        ResponseEntity<PaymentOrderResponse> response = controllerWithMock.createPaymentOrder(request);

        assertNotNull(response.getBody());
        assertEquals("order_mock_12345", response.getBody().getOrderId());
        verify(transactionRepository, times(1)).save(any(PaymentTransaction.class));
    }

    @Test
    @DisplayName("Verification of an already refunded transaction throws BadRequestException")
    void testVerifyPayment_RefundedTransaction_ThrowsBadRequestException() {
        String orderId = "order_refunded_123";
        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_123")
                .razorpaySignature("sig_123")
                .build();

        PaymentTransaction refundedTx = PaymentTransaction.builder()
                .razorpayOrderId(orderId)
                .status("REFUNDED")
                .build();

        when(transactionRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(refundedTx));

        assertThrows(com.bytevault.common.exception.BadRequestException.class, () ->
                paymentController.verifyPayment(request));
    }

    @Test
    @DisplayName("Vendor accessing another vendor's earnings without admin role -> Throws AccessDeniedException")
    void testVendorEarnings_CrossVendorAccess_ThrowsAccessDeniedException() {
        UUID authenticatedVendorId = UUID.randomUUID();
        UUID targetVendorId = UUID.randomUUID();

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                paymentController.getVendorEarnings(targetVendorId, authenticatedVendorId.toString(), "ROLE_VENDOR", null));
    }

    @Test
    @DisplayName("Admin accessing any vendor's earnings via requestedVendorId succeeds")
    void testVendorEarnings_AdminAccess_Succeeds() {
        UUID adminId = UUID.randomUUID();
        UUID targetVendorId = UUID.randomUUID();

        com.bytevault.payment.dto.VendorEarningsResponse mockResponse =
                com.bytevault.payment.dto.VendorEarningsResponse.builder()
                        .vendorId(targetVendorId)
                        .grossEarnings(new BigDecimal("1000.00"))
                        .build();

        when(vendorLedgerService.getVendorEarnings(targetVendorId)).thenReturn(mockResponse);

        ResponseEntity<com.bytevault.payment.dto.VendorEarningsResponse> response =
                paymentController.getVendorEarnings(targetVendorId, adminId.toString(), "ROLE_ADMIN", null);

        assertNotNull(response.getBody());
        assertEquals(targetVendorId, response.getBody().getVendorId());
    }

    @Test
    @DisplayName("Customer retrieving own transaction history succeeds")
    void testGetMyTransactions_ReturnsCustomerTransactions() {
        UUID customerId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .userId(customerId)
                .amount(new BigDecimal("199.00"))
                .status("SUCCESS")
                .build();

        when(transactionRepository.findByUserIdOrderByCreatedAtDesc(customerId)).thenReturn(java.util.List.of(tx));

        ResponseEntity<java.util.List<PaymentTransaction>> response =
                paymentController.getMyTransactions(customerId.toString(), null);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(customerId, response.getBody().get(0).getUserId());
    }

    @Test
    @DisplayName("Non-admin accessing admin all transactions -> Throws AccessDeniedException")
    void testGetAllTransactionsAdmin_NonAdmin_ThrowsAccessDeniedException() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                paymentController.getAllTransactionsAdmin("ROLE_CUSTOMER", null));
    }
}

