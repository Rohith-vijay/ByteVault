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
public class UserRegisteredEventListener {

    private final EmailService emailService;
    private final EmailTemplateBuilder emailTemplateBuilder;

    @RabbitListener(queues = "notification.queue.user.registered")
    public void handleUserRegistered(Map<String, Object> event) {
        log.info("[NotificationService] Consumed UserRegisteredEvent: {}", event);

        try {
            String rawEmail = (String) event.get("email");
            String rawName = (String) event.get("fullName");

            final String email = (rawEmail == null || rawEmail.trim().isEmpty()) ? "user@example.com" : rawEmail;
            final String name = (rawName == null || rawName.trim().isEmpty()) ? "Valued Member" : rawName;

            String subject = "Welcome to ByteVault Media!";
            String content = "<p>Hi " + name + ",</p><p>Welcome to ByteVault Media! Your digital marketplace account has been created successfully.</p>";

            emailService.sendEmail(email, subject, content)
                    .thenRun(() -> log.info("[NotificationService] Welcome email sent to: {}", email))
                    .exceptionally(ex -> {
                        log.error("[NotificationService] Failed to send welcome email to: {}", email, ex);
                        return null;
                    });
        } catch (Exception e) {
            log.error("[NotificationService] Error handling UserRegisteredEvent", e);
        }
    }
}
