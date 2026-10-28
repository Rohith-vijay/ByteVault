package com.bytevault.notification.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.dto.notification.NotificationDto.*;
import com.bytevault.notification.email.EmailService;
import com.bytevault.notification.email.EmailTemplateBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final EmailService emailService;
    private final EmailTemplateBuilder emailTemplateBuilder;

    @PostMapping("/email/verification")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(@Valid @RequestBody VerificationEmailRequest request) {
        log.info("[NotificationService] Received verification email dispatch request for: {}", request.getToEmail());
        String customerName = request.getCustomerName() != null ? request.getCustomerName() : "Customer";
        String htmlBody = emailTemplateBuilder.buildVerificationEmail(customerName, request.getVerificationLink());

        emailService.sendEmail(request.getToEmail(), "Verify Your Email Address - ByteVault", htmlBody);
        return ResponseEntity.ok(ApiResponse.success("Verification email dispatched successfully", null));
    }

    @PostMapping("/email/order-confirmation")
    public ResponseEntity<ApiResponse<Void>> sendOrderConfirmationEmail(@Valid @RequestBody OrderConfirmationEmailRequest request) {
        log.info("[NotificationService] Received order confirmation email dispatch request for order: {}", request.getOrderId());
        String customerName = request.getCustomerName() != null ? request.getCustomerName() : "Customer";
        String downloadLink = request.getDownloadLink() != null ? request.getDownloadLink() : (emailTemplateBuilder.getFrontendUrl() + "/library");
        String htmlBody = emailTemplateBuilder.buildOrderPaidEmail(customerName, request.getOrderId() != null ? request.getOrderId().toString() : "N/A", downloadLink);

        emailService.sendEmail(request.getToEmail(), "Order Confirmed - ByteVault Media", htmlBody);
        return ResponseEntity.ok(ApiResponse.success("Order confirmation email dispatched successfully", null));
    }

    @PostMapping("/email/entitlement-ready")
    public ResponseEntity<ApiResponse<Void>> sendEntitlementReadyEmail(@Valid @RequestBody EntitlementReadyEmailRequest request) {
        log.info("[NotificationService] Received entitlement-ready email dispatch request for order: {}", request.getOrderId());
        String customerName = request.getCustomerName() != null ? request.getCustomerName() : "Customer";
        String downloadLink = request.getDownloadLink() != null ? request.getDownloadLink() : (emailTemplateBuilder.getFrontendUrl() + "/library");
        String htmlBody = emailTemplateBuilder.buildOrderPaidEmail(customerName, request.getOrderId() != null ? request.getOrderId().toString() : "N/A", downloadLink);

        emailService.sendEmail(request.getToEmail(), "Your Digital Content Is Ready For Download - ByteVault", htmlBody);
        return ResponseEntity.ok(ApiResponse.success("Entitlement download notification dispatched successfully", null));
    }

    @PostMapping("/email/password-reset")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetEmail(@Valid @RequestBody PasswordResetEmailRequest request) {
        log.info("[NotificationService] Received password reset email dispatch request for: {}", request.getToEmail());
        String customerName = request.getCustomerName() != null ? request.getCustomerName() : "Customer";
        String htmlBody = emailTemplateBuilder.buildPasswordResetEmail(customerName, request.getResetLink());

        emailService.sendEmail(request.getToEmail(), "Password Reset Request - ByteVault", htmlBody);
        return ResponseEntity.ok(ApiResponse.success("Password reset email dispatched successfully", null));
    }
}
