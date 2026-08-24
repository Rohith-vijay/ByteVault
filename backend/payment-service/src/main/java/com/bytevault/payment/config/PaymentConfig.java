package com.bytevault.payment.config;

import com.razorpay.RazorpayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PaymentConfig {

    @Value("${razorpay.key-id:dummy}")
    private String keyId;

    @Value("${razorpay.key-secret:dummy}")
    private String keySecret;

    @Bean
    public RazorpayClient razorpayClient() {
        if ("dummy".equals(keyId) || "dev_razorpay_key".equals(keyId) || keyId == null || keyId.trim().isEmpty()) {
            log.warn("[PaymentConfig] Razorpay credentials not configured. Using payment simulator for sandbox transactions.");
            return null;
        }
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (Exception e) {
            log.error("[PaymentConfig] Failed to initialize RazorpayClient", e);
            return null;
        }
    }
}
