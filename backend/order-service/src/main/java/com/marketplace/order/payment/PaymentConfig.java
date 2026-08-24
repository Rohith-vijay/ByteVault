package com.marketplace.order.payment;

import com.razorpay.RazorpayClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@Getter
public class PaymentConfig {

    @Value("${razorpay.key-id:dummy}")
    private String keyId;

    @Value("${razorpay.key-secret:dummy}")
    private String keySecret;

    @Bean
    public RazorpayClient razorpayClient() {
        try {
            if ("dummy".equals(keyId) || keyId == null || keyId.isBlank()) {
                log.warn("[PaymentConfig] Razorpay key-id is not configured or set to dummy. Live payments disabled.");
                return new RazorpayClient("dummy_id", "dummy_secret");
            }
            return new RazorpayClient(keyId, keySecret);
        } catch (Exception e) {
            log.error("[PaymentConfig] Failed to initialize RazorpayClient: {}", e.getMessage());
            try {
                return new RazorpayClient("dummy_id", "dummy_secret");
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
