package com.bytevault.identity;

import com.bytevault.common.security.DownstreamSecurityFilter;
import com.bytevault.identity.controller.UserController;
import com.bytevault.identity.dto.UserProfileResponse;
import com.bytevault.identity.service.UserProfileService;
import com.bytevault.identity.service.VendorProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/identity_db",
        "app.gateway.secret=platform_default_gateway_shared_secret"
})
class IdentitySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private VendorProfileService vendorProfileService;

    @Test
    @DisplayName("Security: Missing X-Gateway-Secret header blocks request with 403 Forbidden")
    void testDirectAccessWithoutGatewaySecret_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Forbidden: Direct calls not allowed."));
    }

    @Test
    @DisplayName("Security: Valid Gateway-Secret with authenticated user succeeds (200 OK)")
    void testAuthenticatedUserWithGatewaySecret_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UserProfileResponse mockProfile = UserProfileResponse.builder()
                .userId(userId)
                .email("alice@bytevault.com")
                .fullName("Alice Wonderland")
                .build();

        when(userProfileService.getProfile(any(UUID.class))).thenReturn(mockProfile);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-Gateway-Secret", "platform_default_gateway_shared_secret")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .header("X-User-Email", "alice@bytevault.com")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("alice@bytevault.com"));
    }

    @Test
    @DisplayName("Security: Customer role accessing Admin vendor endpoint is rejected with 403")
    void testCustomerRoleAccessingAdminEndpoint_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors/pending")
                        .header("X-Gateway-Secret", "platform_default_gateway_shared_secret")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Public Auth registration endpoint accessible without prior authentication")
    void testPublicRegistrationEndpoint_Permitted() throws Exception {
        // Direct access to /api/v1/auth/ is exempt from gateway secret filter for public frontend auth
        mockMvc.perform(get("/api/v1/auth/verify?token=fake")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection()); // Redirects on invalid token
    }

    @Test
    @DisplayName("Security: Forged or unverified OAuth token request is rejected with 400 Bad Request")
    void testOauthEndpoint_ForgedOrDisabled_Rejected() throws Exception {
        String forgedPayload = """
            {
                "provider": "google",
                "oauthId": "fake-forged-google-sub-id",
                "email": "attacker@evil.com",
                "fullName": "Fake User"
            }
        """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/oauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedPayload))
                .andExpect(status().isBadRequest());
    }
}

