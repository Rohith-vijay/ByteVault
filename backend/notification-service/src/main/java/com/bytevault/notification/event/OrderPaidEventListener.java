package com.bytevault.notification.event;

import com.bytevault.notification.email.EmailService;
import com.bytevault.notification.email.EmailTemplateBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

    private final EmailService emailService;
    private final EmailTemplateBuilder emailTemplateBuilder;

    @RabbitListener(queues = "notification.queue.order.paid")
    public void handleOrderPaid(Map<String, Object> event) {
        log.info("[OrderPaidEventListener] Consumed OrderPaidEvent: {}", event);

        try {
            String orderId = (String) event.get("orderId");
            String userId = (String) event.get("userId");
            String rawEmail = (String) event.get("customerEmail");
            String rawName = (String) event.get("customerName");

            final String email = (rawEmail == null || rawEmail.trim().isEmpty()) ? "customer@example.com" : rawEmail;
            final String name = (rawName == null || rawName.trim().isEmpty()) ? "Valued Customer" : rawName;

            // Mocks download redirect link to gateway fulfillment portal
            String downloadLink = "http://localhost:8080/api/v1/downloads/";

            String emailContent = emailTemplateBuilder.buildOrderPaidEmail(name, orderId, downloadLink);
            String subject = "Your ByteVault Media Purchase Confirmation - Order: " + orderId;

            emailService.sendEmail(email, subject, emailContent)
                    .thenRun(() -> log.info("[OrderPaidEventListener] Dispatch success for email to: {}", email))
                    .exceptionally(ex -> {
                        log.error("[OrderPaidEventListener] Dispatch failure for email to: {}", email, ex);
                        return null;
                    });

        } catch (Exception e) {
            log.error("[OrderPaidEventListener] Error parsing purchase notification payload", e);
        }
    }
}
