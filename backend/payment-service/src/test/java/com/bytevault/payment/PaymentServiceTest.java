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
    private com.bytevault.payment.client.OrderClient orderClient;

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
        paymentController = new PaymentController(razorpayPaymentService, vendorLedgerService, refundService, transactionRepository, paymentOrderSyncService, orderClient);
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

        UUID dbOrderId = UUID.randomUUID();
        request.setDbOrderId(dbOrderId.toString());

        when(transactionRepository.findByRazorpayOrderId("order_12345"))
                .thenReturn(Optional.of(PaymentTransaction.builder().status("PENDING").build()));

        assertThrows(IllegalArgumentException.class, () -> paymentController.verifyPayment(request));
        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
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
    }

    @Test
    @DisplayName("Razorpay webhook callback handles event when webhook secret is unconfigured (inert/dev mode)")
    void testRazorpayWebhook_NoSecret_Inert() {
        org.springframework.test.util.ReflectionTestUtils.setField(paymentController, "webhookSecret", "");
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
        ResponseEntity<Map<String, Object>> response = paymentController.handleRazorpayWebhook(null, payload);

        assertNotNull(response.getBody());
        assertEquals("PROCESSED", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Razorpay webhook rejects request when secret configured and signature is missing (400 Bad Request)")
    void testRazorpayWebhook_MissingSignature_Rejected() {
        org.springframework.test.util.ReflectionTestUtils.setField(paymentController, "webhookSecret", "whsec_test_secret_12345");
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
        ResponseEntity<Map<String, Object>> response = paymentController.handleRazorpayWebhook(null, payload);

        assertNotNull(response.getBody());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("REJECTED", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Razorpay webhook rejects request when secret configured and signature is invalid (400 Bad Request)")
    void testRazorpayWebhook_InvalidSignature_Rejected() {
        org.springframework.test.util.ReflectionTestUtils.setField(paymentController, "webhookSecret", "whsec_test_secret_12345");
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
        ResponseEntity<Map<String, Object>> response = paymentController.handleRazorpayWebhook("invalid_forged_signature_hex", payload);

        assertNotNull(response.getBody());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("REJECTED", response.getBody().get("status"));
    }

    @Test
    @DisplayName("Razorpay webhook accepts and processes event with valid HMAC-SHA256 signature (200 OK)")
    void testRazorpayWebhook_ValidSignature_Accepted() throws Exception {
        String secret = "whsec_test_secret_12345";
        org.springframework.test.util.ReflectionTestUtils.setField(paymentController, "webhookSecret", secret);
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String validSignature = sb.toString();

        ResponseEntity<Map<String, Object>> response = paymentController.handleRazorpayWebhook(validSignature, payload);

        assertNotNull(response.getBody());
        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
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
                mockPaymentService, vendorLedgerService, refundService, transactionRepository, paymentOrderSyncService, orderClient);

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

    @Test
    @DisplayName("Simulator Safety: createOrder creates mock order when simulator enabled with dummy keys")
    void testSimulatorSafety_EnabledWithDummyKeys_CreatesMockOrder() throws Exception {
        RazorpayPaymentService service = new RazorpayPaymentService(mock(com.bytevault.payment.service.RazorpayClientService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "simulatorEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keyId", "dummy");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keySecret", "dummy");

        com.bytevault.payment.dto.PaymentOrderResponse res = service.createOrder(
                com.bytevault.payment.dto.PaymentOrderRequest.builder()
                        .amount(new BigDecimal("100.00"))
                        .currency("INR")
                        .receipt(UUID.randomUUID().toString())
                        .build()
        );

        assertNotNull(res);
        assertTrue(res.getOrderId().startsWith("order_mock_"));
    }

    @Test
    @DisplayName("Simulator Safety: createOrder throws IllegalStateException when simulator disabled and no real keys")
    void testSimulatorSafety_DisabledWithDummyKeys_ThrowsException() {
        RazorpayPaymentService service = new RazorpayPaymentService(mock(com.bytevault.payment.service.RazorpayClientService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "simulatorEnabled", false);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keyId", "dummy");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keySecret", "dummy");

        assertThrows(IllegalStateException.class, () -> service.createOrder(
                com.bytevault.payment.dto.PaymentOrderRequest.builder()
                        .amount(new BigDecimal("100.00"))
                        .currency("INR")
                        .receipt(UUID.randomUUID().toString())
                        .build()
        ));
    }

    @Test
    @DisplayName("Simulator Safety: Auto-disables simulator when real Razorpay keys are detected")
    void testSimulatorSafety_AutoDisablesWhenRealKeysDetected() throws Exception {
        com.bytevault.payment.service.RazorpayClientService mockClient = mock(com.bytevault.payment.service.RazorpayClientService.class);
        com.razorpay.Order mockOrder = mock(com.razorpay.Order.class);
        when(mockOrder.get("id")).thenReturn("order_real_rzp_9999");
        when(mockClient.createOrder(any(), any(), any())).thenReturn(mockOrder);

        RazorpayPaymentService service = new RazorpayPaymentService(mockClient);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "simulatorEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keyId", "rzp_live_abc123xyz");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keySecret", "secret_live_999");

        com.bytevault.payment.dto.PaymentOrderResponse res = service.createOrder(
                com.bytevault.payment.dto.PaymentOrderRequest.builder()
                        .amount(new BigDecimal("250.00"))
                        .currency("INR")
                        .receipt(UUID.randomUUID().toString())
                        .build()
        );

        assertNotNull(res);
        assertEquals("order_real_rzp_9999", res.getOrderId());
        verify(mockClient, times(1)).createOrder(any(), any(), any());
    }

    @Test
    @DisplayName("Simulator Safety: verifyPayment rejects mock order when simulator is disabled")
    void testSimulatorSafety_VerifyMockOrderRejectedWhenSimulatorDisabled() {
        RazorpayPaymentService service = new RazorpayPaymentService(mock(com.bytevault.payment.service.RazorpayClientService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "simulatorEnabled", false);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keyId", "dummy");

        PaymentVerifyRequest verifyReq = PaymentVerifyRequest.builder()
                .razorpayOrderId("order_mock_12345678")
                .razorpayPaymentId("pay_mock_1234")
                .razorpaySignature("sig_mock_1234")
                .build();

        assertThrows(IllegalStateException.class, () -> service.verifyPayment(verifyReq));
    }
}

