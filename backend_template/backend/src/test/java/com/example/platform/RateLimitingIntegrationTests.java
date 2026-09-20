package com.example.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.auth.AuthenticationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rate-limit.auth.capacity=5",
        "rate-limit.auth.refill-tokens=5",
        "rate-limit.auth.refill-seconds=60"
})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class RateLimitingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void whenExceedingAuthRateLimit_thenReturns429() throws Exception {
        AuthenticationRequest loginReq = new AuthenticationRequest("wronguser@example.com", "wrongpass");
        String payload = objectMapper.writeValueAsString(loginReq);

        // First 5 attempts should return 401 (Unauthorized) due to invalid credentials, not 429
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt must return 429 (Too Many Requests)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests());
    }
}
