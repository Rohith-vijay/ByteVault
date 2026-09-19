package com.bytevault.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

public class JwtGatewayFilterTest {

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
    @DisplayName("Public auth path passes without JWT")
    void testPublicAuthRoute() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-User-Id", "spoofed-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(argThat(ex -> {
            var mutatedHeaders = ex.getRequest().getHeaders();
            // Verify spoofed identity header is stripped and gateway secret is attached
            return !mutatedHeaders.containsKey("X-User-Id") &&
                    gatewaySecret.equals(mutatedHeaders.getFirst("X-Gateway-Secret"));
        }));
    }

    @Test
    @DisplayName("Public product catalog GET passes without JWT")
    void testPublicCatalogGetRoute() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(any());
    }

    @Test
    @DisplayName("Protected endpoint with valid JWT -> Decodes claims and attaches trusted headers")
    void testProtectedEndpointWithValidJwt() {
        UUID userId = UUID.randomUUID();
        String token = generateToken("customer@bytevault.com", "ROLE_CUSTOMER", userId, 3600000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(argThat(ex -> {
            var headers = ex.getRequest().getHeaders();
            return userId.toString().equals(headers.getFirst("X-User-Id")) &&
                    "ROLE_CUSTOMER".equals(headers.getFirst("X-User-Roles")) &&
                    "customer@bytevault.com".equals(headers.getFirst("X-User-Email")) &&
                    gatewaySecret.equals(headers.getFirst("X-Gateway-Secret"));
        }));
    }

    @Test
    @DisplayName("Protected endpoint with missing token -> 401 Unauthorized")
    void testProtectedEndpointMissingToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Protected endpoint with expired token -> 401 Unauthorized")
    void testProtectedEndpointExpiredToken() {
        UUID userId = UUID.randomUUID();
        String expiredToken = generateToken("customer@bytevault.com", "ROLE_CUSTOMER", userId, -3600000);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/orders")
                .header("Authorization", "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Protected endpoint with tampered or malformed token -> 401 Unauthorized")
    void testProtectedEndpointTamperedToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/orders")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.invalid_payload.tampered_signature")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Exceeding rate limit threshold -> Returns 429 Too Many Requests")
    void testRateLimitExceeded() {
        ReflectionTestUtils.setField(filter, "maxRequestsPerMinute", 2);

        MockServerHttpRequest req1 = MockServerHttpRequest.post("/api/v1/auth/login").build();
        MockServerHttpRequest req2 = MockServerHttpRequest.post("/api/v1/auth/login").build();
        MockServerHttpRequest req3 = MockServerHttpRequest.post("/api/v1/auth/login").build();

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
        MockServerWebExchange ex3 = MockServerWebExchange.from(req3);

        filter.filter(ex1, chain).block();
        filter.filter(ex2, chain).block();
        filter.filter(ex3, chain).block();

        // Third request exceeds limit
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex3.getResponse().getStatusCode());
    }
}
