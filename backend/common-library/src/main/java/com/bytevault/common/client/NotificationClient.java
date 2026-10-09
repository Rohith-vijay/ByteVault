package com.bytevault.common.client;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.config.CommonFeignConfig;
import com.bytevault.common.dto.notification.NotificationDto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "notification-service",
        url = "${app.services.notification-service.url:}",
        configuration = CommonFeignConfig.class
)
public interface NotificationClient {

    @PostMapping("/api/v1/notifications/email/verification")
    ApiResponse<Void> sendVerificationEmail(@RequestBody VerificationEmailRequest request);

    @PostMapping("/api/v1/notifications/email/order-confirmation")
    ApiResponse<Void> sendOrderConfirmationEmail(@RequestBody OrderConfirmationEmailRequest request);

    @PostMapping("/api/v1/notifications/email/entitlement-ready")
    ApiResponse<Void> sendEntitlementReadyEmail(@RequestBody EntitlementReadyEmailRequest request);

    @PostMapping("/api/v1/notifications/email/password-reset")
    ApiResponse<Void> sendPasswordResetEmail(@RequestBody PasswordResetEmailRequest request);
}
