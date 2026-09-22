package com.bytevault.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.core.Ordered;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtGatewayFilter implements WebFilter, Ordered {

    // Must run BEFORE Spring Cloud Gateway routing (order 0) so we can intercept
    // OPTIONS preflight requests before they are forwarded to downstream services.
    @Override
    public int getOrder() {
        return -1;
    }

    @Value("${app.jwt.secret:default_super_secure_jwt_secret_placeholder_minimum_32_chars_long}")
    private String jwtSecret = "default_super_secure_jwt_secret_placeholder_minimum_32_chars_long";

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret = "platform_default_gateway_shared_secret";

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> requestCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> windowStartTimes = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Value("${app.rate-limit.requests-per-minute:5000}")
    private int maxRequestsPerMinute = 5000;

    private boolean isRateLimited(String clientKey) {
        long now = System.currentTimeMillis();
        windowStartTimes.compute(clientKey, (k, startTime) -> {
            if (startTime == null || now - startTime > 60000) {
                requestCounts.put(clientKey, new java.util.concurrent.atomic.AtomicInteger(0));
                return now;
            }
            return startTime;
        });

        int currentCount = requestCounts.computeIfAbsent(clientKey, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
        return currentCount > maxRequestsPerMinute;
    }

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 1. Manage Correlation ID
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        boolean isDocOrOption = HttpMethod.OPTIONS.equals(method)
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator")
                || path.startsWith("/webjars");

        // Rate Limiting check on API paths
        if (!isDocOrOption) {
            String clientIp = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "anonymous";
            String rateLimitKey = clientIp + ":" + (path.startsWith("/api/v1/auth") ? "auth" : "general");
            
            if (isRateLimited(rateLimitKey)) {
                log.warn("Rate limit exceeded for key: {}", rateLimitKey);
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(
                        "{\"success\":false,\"message\":\"Rate limit exceeded. Please try again later.\"}"
                        .getBytes(StandardCharsets.UTF_8))));
            }
        }

        // 2. Short-circuit ALL OPTIONS (CORS preflight) requests HERE.
        // Spring Cloud Gateway routes match OPTIONS requests and forward them to downstream services,
        // which return 403. We must intercept OPTIONS BEFORE routing occurs (this filter runs first).
        if (HttpMethod.OPTIONS.equals(method)) {
            String origin = request.getHeaders().getFirst("Origin");
            response.setStatusCode(HttpStatus.OK);
            if (origin != null) {
                response.getHeaders().set("Access-Control-Allow-Origin", origin);
            }
            response.getHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD");
            response.getHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Correlation-ID, X-Requested-With, Accept");
            response.getHeaders().set("Access-Control-Allow-Credentials", "true");
            response.getHeaders().set("Access-Control-Max-Age", "3600");
            response.getHeaders().setContentLength(0);
            return response.setComplete();
        }

        // 3. Determine public endpoints
        boolean isPublicAuth = path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars");

        boolean isPublicCatalogGet = HttpMethod.GET.equals(method) && 
                (path.startsWith("/api/v1/products") || path.startsWith("/api/v1/categories")) &&
                !path.contains("/download-url");

        boolean isPublicPath = isPublicAuth || isPublicCatalogGet;


        // Reject any external caller attempting to reach internal service routes
        if (path.contains("/internal/")) {
            log.warn("Blocked external attempt to access internal service route: {}", path);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"success\":false,\"message\":\"Access Forbidden: Direct access to internal endpoints is blocked.\"}"
                    .getBytes(StandardCharsets.UTF_8))));
        }

        // 3. Prevent Header Spoofing: Strip untrusted client identity headers
        ServerHttpRequest.Builder mutatedRequestBuilder = request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Roles");
                    headers.remove("X-User-Email");
                    headers.remove("X-Gateway-Secret");
                })
                .header(CORRELATION_ID_HEADER, correlationId)
                .header("X-Gateway-Secret", gatewaySecret);

        String authHeader = request.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION);

        // If public path and no Authorization header, forward request directly
        if (isPublicPath && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            return chain.filter(exchange.mutate().request(mutatedRequestBuilder.build()).build());
        }

        // 4. If token is missing on protected endpoint
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Blocked request to: {} - Missing authorization header", path);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"success\":false,\"message\":\"Access Unauthorized: Missing or invalid token.\"}"
                    .getBytes(StandardCharsets.UTF_8))));
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (claims.getExpiration().before(new Date())) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(
                        "{\"success\":false,\"message\":\"Access Unauthorized: Token expired.\"}"
                        .getBytes(StandardCharsets.UTF_8))));
            }

            // Extract verified claims
            String userId = (String) claims.get("id");
            String role = (String) claims.get("role");
            String email = claims.getSubject();

            // Attach verified trusted headers
            if (userId != null) mutatedRequestBuilder.header("X-User-Id", userId);
            if (role != null) mutatedRequestBuilder.header("X-User-Roles", role);
            if (email != null) mutatedRequestBuilder.header("X-User-Email", email);

            return chain.filter(exchange.mutate().request(mutatedRequestBuilder.build()).build());

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"success\":false,\"message\":\"Access Unauthorized: Token expired.\"}"
                    .getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"success\":false,\"message\":\"Access Unauthorized: Token validation failed.\"}"
                    .getBytes(StandardCharsets.UTF_8))));
        }
    }
}
