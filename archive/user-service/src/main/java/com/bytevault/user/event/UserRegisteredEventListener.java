package com.bytevault.user.event;

import com.bytevault.user.service.UserProfileService;
import com.bytevault.user.service.VendorProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {

    private final UserProfileService userProfileService;
    private final VendorProfileService vendorProfileService;

    @RabbitListener(queues = "user.queue.registration")
    public void handleUserRegistered(Map<String, Object> event) {
        log.info("[UserRegisteredEventListener] Received UserRegisteredEvent: {}", event);
        try {
            String userIdStr = (String) event.get("userId");
            String email = (String) event.get("email");
            String fullName = (String) event.get("fullName");
            String role = (String) event.get("role");

            if (userIdStr != null && email != null) {
                UUID userId = UUID.fromString(userIdStr);
                userProfileService.createProfileFromEvent(userId, email, fullName);

                if ("VENDOR".equalsIgnoreCase(role)) {
                    String storeName = (String) event.get("storeName");
                    String storeDescription = (String) event.get("storeDescription");
                    String businessTaxId = (String) event.get("businessTaxId");
                    String payoutInfo = (String) event.get("payoutInfo");
                    String supportEmail = (String) event.get("supportEmail");

                    vendorProfileService.createPendingVendorProfile(
                            userId,
                            email,
                            storeName,
                            storeDescription,
                            businessTaxId,
                            payoutInfo,
                            supportEmail
                    );
                }
            }
        } catch (Exception e) {
            log.error("[UserRegisteredEventListener] Error processing registration event", e);
        }
    }
}

