package com.bytevault.gateway.integration;

import com.bytevault.gateway.security.JwtGatewayFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GatewaySecurityAndRoutingIntegrationTest {

    private JwtGatewayFilter filter;
    private final String secretKey = "test_super_secure_jwt_secret_key_minimum_32_chars_long!";
    private final String gatewaySecret = "test_gateway_secret";

    @BeforeEach
    void setUp() {
        filter = new JwtGatewayFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", secretKey);
        ReflectionTestUtils.setField(filter, "gatewaySecret", gatewaySecret);
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 100);
    }

    private String generateToken(String subject, String role, UUID userId, long expiryDurationMs) {
        Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(subject)
                .claim("id", userId.toString())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiryDurationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("CORS preflight OPTIONS request is passed through safely")
    void testCorsPreflight() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/api/v1/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
    }

    @Test
    @DisplayName("Client injecting forged X-User-Id / X-User-Roles has spoofed headers stripped")
    void testHeaderSpoofingProtection() {
        UUID realUserId = UUID.randomUUID();
        String token = generateToken("realuser@bytevault.com", "ROLE_CUSTOMER", realUserId, 3600000);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", "forged-admin-uuid")
                .header("X-User-Roles", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            // Assert that forged headers are replaced with authenticated claims
            return realUserId.toString().equals(headers.getFirst("X-User-Id")) &&
                    "ROLE_CUSTOMER".equals(headers.getFirst("X-User-Roles")) &&
                    gatewaySecret.equals(headers.getFirst("X-Gateway-Secret"));
        }));
    }

    @Test
    @DisplayName("Rate limiting returns 429 after exceeding request limit threshold")
    void testRateLimitEnforcement() {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 3);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.post("/api/v1/auth/login")
                    .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            filter.filter(ex, chain).block();
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getResponse().getStatusCode());
        }

        // 4th request must be 429
        MockServerHttpRequest req4 = MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 12345))
                .build();
        MockServerWebExchange ex4 = MockServerWebExchange.from(req4);
        filter.filter(ex4, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex4.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Tampered JWT signature rejected with HTTP 401")
    void testTamperedJwtSignatureRejection() {
        UUID userId = UUID.randomUUID();
        // Generate valid token and tamper with the signature part
        String token = generateToken("user@bytevault.com", "ROLE_CUSTOMER", userId, 3600000);
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "tamperedSignature123";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header("Authorization", "Bearer " + tamperedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }
}
