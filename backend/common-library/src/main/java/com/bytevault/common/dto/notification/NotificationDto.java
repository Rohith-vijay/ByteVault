package com.bytevault.common.dto.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

public class NotificationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationEmailRequest {
        @NotBlank
        @Email
        private String toEmail;
        private String customerName;
        @NotBlank
        private String verificationLink;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderConfirmationEmailRequest {
        @NotBlank
        @Email
        private String toEmail;
        private String customerName;
        private UUID orderId;
        private String orderNumber;
        private BigDecimal totalAmount;
        private String downloadLink;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntitlementReadyEmailRequest {
        @NotBlank
        @Email
        private String toEmail;
        private String customerName;
        private UUID orderId;
        private String downloadLink;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordResetEmailRequest {
        @NotBlank
        @Email
        private String toEmail;
        private String customerName;
        @NotBlank
        private String resetLink;
    }
}
