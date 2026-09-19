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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private RabbitTemplate rabbitTemplate;

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
        paymentController = new PaymentController(razorpayPaymentService, vendorLedgerService, refundService, rabbitTemplate, transactionRepository, paymentOrderSyncService);
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

        ResponseEntity<Map<String, Object>> response = paymentController.verifyPayment(request);

        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("SUCCESS", tx.getStatus());

        verify(paymentOrderSyncService, times(1)).syncPaymentToOrder(any());
    }

    @Test
    @DisplayName("Tampered signature fails HMAC validation and publishes payment.failed event")
    void testTamperedSignaturePaymentVerification() throws Exception {
        PaymentVerifyRequest request = PaymentVerifyRequest.builder()
                .dbOrderId(UUID.randomUUID().toString())
                .razorpayOrderId("order_rzp_123")
                .razorpayPaymentId("pay_rzp_456")
                .razorpaySignature("forged_signature_xyz")
                .build();

        when(transactionRepository.findByRazorpayOrderId("order_rzp_123"))
                .thenReturn(Optional.of(PaymentTransaction.builder().status("PENDING").build()));

        assertThrows(IllegalArgumentException.class, () -> paymentController.verifyPayment(request));

        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
        verify(rabbitTemplate, times(1)).convertAndSend(eq("order.exchange"), eq("payment.failed"), any(Map.class));
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

        ResponseEntity<Map<String, Object>> response = paymentController.verifyPayment(request);

        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertTrue(response.getBody().get("message").toString().contains("idempotent"));

        // Verify no duplicate Feign call or RabbitMQ event
        verify(paymentOrderSyncService, never()).syncPaymentToOrder(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Map.class));
    }
}
