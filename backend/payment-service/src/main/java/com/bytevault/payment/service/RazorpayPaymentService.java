package com.bytevault.payment.service;

import com.razorpay.Order;
import com.bytevault.payment.dto.PaymentOrderRequest;
import com.bytevault.payment.dto.PaymentOrderResponse;
import com.bytevault.payment.dto.PaymentVerifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private PaymentSimulator paymentSimulator;

    @Value("${app.payment.simulator.enabled:false}")
    private boolean simulatorEnabled;

    @Value("${razorpay.key-id:dummy}")
    private String keyId;

    @Value("${razorpay.key-secret:dummy}")
    private String keySecret;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (isSimulatorActive()) {
            log.warn("====================================================================");
            log.warn("[SECURITY WARNING] Payment Simulator is ENABLED.");
            log.warn("Simulated checkout and payment verification are active.");
            log.warn("DO NOT RUN WITH SIMULATOR ENABLED IN PRODUCTION ENVIRONMENTS.");
            log.warn("====================================================================");
        } else if (simulatorEnabled && hasRealKeys()) {
            log.info("[RazorpayPaymentService] Real Razorpay API keys detected. Simulator auto-disabled.");
        }
    }

    public boolean isSimulatorActive() {
        return simulatorEnabled && !hasRealKeys();
    }

    private boolean hasRealKeys() {
        return keyId != null && !keyId.trim().isEmpty()
                && !"dummy".equalsIgnoreCase(keyId.trim())
                && !"dev_razorpay_key".equalsIgnoreCase(keyId.trim());
    }

    @Override
    public PaymentOrderResponse createOrder(PaymentOrderRequest request) throws Exception {
        String orderId;
        boolean simulatorActive = isSimulatorActive();

        if (simulatorActive) {
            orderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[RazorpayPaymentService] Simulated order created for Sandbox checkout: {}", orderId);
        } else if (!hasRealKeys()) {
            throw new IllegalStateException("Payment simulation is disabled and no valid Razorpay credentials are configured.");
        } else {
            Order order = razorpayClientService.createOrder(request.getAmount(), request.getCurrency(), request.getReceipt());
            orderId = order.get("id");
            log.info("[RazorpayPaymentService] Real Razorpay order created on gateway: {}", orderId);
        }

        return PaymentOrderResponse.builder()
                .orderId(orderId)
                .amount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .key(simulatorActive ? "dummy" : keyId)
                .build();
    }

    @Override
    public void verifyPayment(PaymentVerifyRequest request) throws Exception {
        boolean isMock = request.getRazorpayOrderId() != null && request.getRazorpayOrderId().startsWith("order_mock_");

        if (isMock) {
            if (!isSimulatorActive()) {
                throw new IllegalStateException("Sandbox payment verification is disabled.");
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
