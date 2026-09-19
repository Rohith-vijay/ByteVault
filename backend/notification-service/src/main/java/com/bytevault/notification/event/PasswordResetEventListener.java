package com.bytevault.notification.event;

import com.bytevault.notification.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetEventListener {

    private final EmailService emailService;

    @RabbitListener(queues = "notification.queue.password.reset")
    public void handlePasswordReset(Map<String, Object> event) {
        log.info("[NotificationService] Consumed PasswordResetEvent: {}", event);

        try {
            String email = (String) event.get("email");
            String resetToken = (String) event.get("resetToken");

            if (email != null && resetToken != null) {
                String subject = "Reset Your ByteVault Media Password";
                String content = "<p>Hello,</p><p>We received a request to reset your ByteVault Media password.</p>" +
                        "<p>Your reset token is: <strong>" + resetToken + "</strong></p>" +
                        "<p>If you did not request this, you can safely ignore this email.</p>";

                emailService.sendEmail(email, subject, content)
                        .thenRun(() -> log.info("[NotificationService] Password reset email sent to: {}", email))
                        .exceptionally(ex -> {
                            log.error("[NotificationService] Failed to send password reset email to: {}", email, ex);
                            return null;
                        });
            }
        } catch (Exception e) {
            log.error("[NotificationService] Error handling PasswordResetEvent", e);
        }
    }
}
