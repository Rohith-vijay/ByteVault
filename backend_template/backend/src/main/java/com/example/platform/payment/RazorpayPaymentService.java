package com.example.platform.payment;

import com.razorpay.Order;
import com.example.platform.payment.dto.PaymentOrderRequest;
import com.example.platform.payment.dto.PaymentOrderResponse;
import com.example.platform.payment.dto.PaymentVerifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayPaymentService implements PaymentService {

    private final RazorpayClientService razorpayClientService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentSimulator paymentSimulator;

    @Value("${razorpay.key-id:dummy}")
    private String keyId;

    @Value("${razorpay.key-secret:dummy}")
    private String keySecret;

    @Override
    public PaymentOrderResponse createOrder(PaymentOrderRequest request) throws Exception {
        String orderId;
        boolean isSandbox = "dev_razorpay_key".equals(keyId) || "dummy".equals(keyId) || keyId == null || keyId.trim().isEmpty();
        
        if (isSandbox) {
            if (paymentSimulator == null) {
                throw new IllegalArgumentException("Sandbox checkout simulation is strictly disabled in production.");
            }
            orderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[RazorpayPaymentService] Simulated order created for Sandbox checkout: {}", orderId);
        } else {
            Order order = razorpayClientService.createOrder(request.getAmount(), request.getCurrency(), request.getReceipt());
            orderId = order.get("id");
            log.info("[RazorpayPaymentService] Real Razorpay order created on gateway: {}", orderId);
        }

        return PaymentOrderResponse.builder()
                .orderId(orderId)
                .amount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .key(isSandbox ? "dummy" : keyId)
                .build();
    }

    @Override
    public void verifyPayment(PaymentVerifyRequest request) throws Exception {
        boolean isMock = request.getRazorpayOrderId() != null && request.getRazorpayOrderId().startsWith("order_mock_");

        if (isMock) {
            if (paymentSimulator == null) {
                throw new IllegalArgumentException("Sandbox payment verification is strictly disabled in production.");
            }
            log.info("[RazorpayPaymentService] Verifying mock order sandbox transaction: {}", request.getRazorpayOrderId());
        } else {
            String payload = request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId();
            String generatedSignature = hmacSha256Hex(payload, keySecret);

            if (request.getRazorpaySignature() == null || !java.security.MessageDigest.isEqual(
                    generatedSignature.getBytes(StandardCharsets.UTF_8),
                    request.getRazorpaySignature().getBytes(StandardCharsets.UTF_8))) {
                log.error("[RazorpayPaymentService] Payment signature verification failed");
                throw new IllegalArgumentException("Invalid payment signature");
            }
            log.info("[RazorpayPaymentService] Payment signature verified successfully for order: {}", request.getRazorpayOrderId());
        }
    }

    private String hmacSha256Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);

        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();

        for (byte b : hash) {
            String hex = java.lang.Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
