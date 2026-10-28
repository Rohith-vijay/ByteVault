package com.bytevault.notification;

import com.bytevault.common.dto.notification.NotificationDto.VerificationEmailRequest;
import com.bytevault.notification.email.EmailService;
import com.bytevault.notification.email.EmailTemplateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perimeter Security & Authentication Tests for Notification Service (CO-2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "app.gateway.secret=platform_default_gateway_shared_secret"
})
class NotificationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailService emailService;

    @MockBean
    private EmailTemplateBuilder emailTemplateBuilder;

    @Test
    @DisplayName("Security: Direct downstream request without X-Gateway-Secret is rejected with 403 Forbidden")
    void testDirectCallWithoutGatewaySecret_Forbidden() throws Exception {
        VerificationEmailRequest request = VerificationEmailRequest.builder()
                .toEmail("user@bytevault.com")
                .customerName("User")
                .verificationLink("https://bytevault.media/verify?token=123")
                .build();

        mockMvc.perform(post("/api/v1/notifications/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Forbidden: Direct calls not allowed."));
    }

    @Test
    @DisplayName("Security: Downstream request with valid X-Gateway-Secret succeeds (200 OK)")
    void testAuthorizedGatewayCall_Success() throws Exception {
        when(emailTemplateBuilder.buildVerificationEmail(anyString(), anyString())).thenReturn("<html>Email</html>");
        when(emailService.sendEmail(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        VerificationEmailRequest request = VerificationEmailRequest.builder()
                .toEmail("user@bytevault.com")
                .customerName("User")
                .verificationLink("https://bytevault.media/verify?token=123")
                .build();

        mockMvc.perform(post("/api/v1/notifications/email/verification")
                        .header("X-Gateway-Secret", "platform_default_gateway_shared_secret")
                        .header("X-User-Id", "system-internal")
                        .header("X-User-Roles", "ROLE_INTERNAL_SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
