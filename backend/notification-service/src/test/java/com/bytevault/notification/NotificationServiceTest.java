package com.bytevault.notification;

import com.bytevault.notification.email.EmailService;
import com.bytevault.notification.email.EmailTemplateBuilder;
import com.bytevault.notification.event.OrderPaidEventListener;
import com.bytevault.notification.event.PasswordResetEventListener;
import com.bytevault.notification.event.UserRegisteredEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailTemplateBuilder emailTemplateBuilder;

    @InjectMocks
    private UserRegisteredEventListener userRegisteredEventListener;

    @InjectMocks
    private PasswordResetEventListener passwordResetEventListener;

    @Test
    @DisplayName("UserRegisteredEvent triggers welcome notification")
    void testUserRegisteredNotification() {
        when(emailService.sendEmail(eq("john@bytevault.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Map<String, Object> event = new HashMap<>();
        event.put("email", "john@bytevault.com");
        event.put("fullName", "John Doe");

        userRegisteredEventListener.handleUserRegistered(event);

        verify(emailService, times(1)).sendEmail(eq("john@bytevault.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("PasswordResetEvent triggers reset token notification")
    void testPasswordResetNotification() {
        when(emailService.sendEmail(eq("alice@bytevault.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Map<String, Object> event = new HashMap<>();
        event.put("email", "alice@bytevault.com");
        event.put("resetToken", "token-xyz-123");

        passwordResetEventListener.handlePasswordReset(event);

        verify(emailService, times(1)).sendEmail(eq("alice@bytevault.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("OrderPaidEvent triggers order confirmation notification")
    void testOrderPaidNotification() {
        OrderPaidEventListener orderPaidListener = new OrderPaidEventListener(emailService, emailTemplateBuilder);
        when(emailTemplateBuilder.buildOrderPaidEmail(any(), any(), any()))
                .thenReturn("<html>Order confirmation</html>");
        when(emailService.sendEmail(eq("buyer@bytevault.com"), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        Map<String, Object> event = new HashMap<>();
        event.put("customerEmail", "buyer@bytevault.com");
        event.put("customerName", "Jane Buyer");
        event.put("orderId", "order-123");
        event.put("totalAmount", "1999.00");

        orderPaidListener.handleOrderPaid(event);

        verify(emailService, times(1)).sendEmail(eq("buyer@bytevault.com"), anyString(), any());
    }
}
