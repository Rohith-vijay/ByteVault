package com.bytevault.notification;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.dto.notification.NotificationDto.*;
import com.bytevault.notification.controller.NotificationController;
import com.bytevault.notification.email.EmailService;
import com.bytevault.notification.email.EmailTemplateBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationControllerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailTemplateBuilder emailTemplateBuilder;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    @DisplayName("Verification email endpoint dispatches email and returns 200 OK")
    void testSendVerificationEmail() {
        when(emailTemplateBuilder.buildVerificationEmail(eq("Alice"), anyString()))
                .thenReturn("<html>Verification HTML</html>");
        when(emailService.sendEmail(eq("alice@bytevault.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        VerificationEmailRequest request = VerificationEmailRequest.builder()
                .toEmail("alice@bytevault.com")
                .customerName("Alice")
                .verificationLink("https://bytevault.media/verify?token=abc")
                .build();

        ResponseEntity<ApiResponse<Void>> response = notificationController.sendVerificationEmail(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService, times(1)).sendEmail(eq("alice@bytevault.com"), anyString(), eq("<html>Verification HTML</html>"));
    }

    @Test
    @DisplayName("Order confirmation email endpoint dispatches email and returns 200 OK")
    void testSendOrderConfirmationEmail() {
        UUID orderId = UUID.randomUUID();
        when(emailTemplateBuilder.buildOrderPaidEmail(eq("Bob"), eq(orderId.toString()), anyString()))
                .thenReturn("<html>Order Paid HTML</html>");
        when(emailService.sendEmail(eq("bob@bytevault.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        OrderConfirmationEmailRequest request = OrderConfirmationEmailRequest.builder()
                .toEmail("bob@bytevault.com")
                .customerName("Bob")
                .orderId(orderId)
                .orderNumber("ORD-1001")
                .totalAmount(BigDecimal.valueOf(1499.00))
                .downloadLink("https://bytevault.media/library")
                .build();

        ResponseEntity<ApiResponse<Void>> response = notificationController.sendOrderConfirmationEmail(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService, times(1)).sendEmail(eq("bob@bytevault.com"), anyString(), eq("<html>Order Paid HTML</html>"));
    }

    @Test
    @DisplayName("Entitlement ready email endpoint dispatches download email and returns 200 OK")
    void testSendEntitlementReadyEmail() {
        UUID orderId = UUID.randomUUID();
        when(emailTemplateBuilder.buildOrderPaidEmail(eq("Charlie"), eq(orderId.toString()), anyString()))
                .thenReturn("<html>Download Ready HTML</html>");
        when(emailService.sendEmail(eq("charlie@bytevault.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        EntitlementReadyEmailRequest request = EntitlementReadyEmailRequest.builder()
                .toEmail("charlie@bytevault.com")
                .customerName("Charlie")
                .orderId(orderId)
                .downloadLink("https://bytevault.media/library/downloads")
                .build();

        ResponseEntity<ApiResponse<Void>> response = notificationController.sendEntitlementReadyEmail(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService, times(1)).sendEmail(eq("charlie@bytevault.com"), anyString(), eq("<html>Download Ready HTML</html>"));
    }

    @Test
    @DisplayName("Password reset email endpoint dispatches reset link and returns 200 OK")
    void testSendPasswordResetEmail() {
        when(emailTemplateBuilder.buildPasswordResetEmail(eq("David"), anyString()))
                .thenReturn("<html>Reset Password HTML</html>");
        when(emailService.sendEmail(eq("david@bytevault.com"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        PasswordResetEmailRequest request = PasswordResetEmailRequest.builder()
                .toEmail("david@bytevault.com")
                .customerName("David")
                .resetLink("https://bytevault.media/reset-password?token=xyz")
                .build();

        ResponseEntity<ApiResponse<Void>> response = notificationController.sendPasswordResetEmail(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(emailService, times(1)).sendEmail(eq("david@bytevault.com"), anyString(), eq("<html>Reset Password HTML</html>"));
    }
}
