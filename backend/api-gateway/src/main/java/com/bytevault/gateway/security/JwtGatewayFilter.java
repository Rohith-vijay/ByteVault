package com.bytevault.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtGatewayFilter implements WebFilter {

    @Value("${app.jwt.secret:default_super_secure_jwt_secret_placeholder_minimum_32_chars_long}")
    private String jwtSecret;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

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
        
        // Propagate to Response header
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        // Path checking
        String path = request.getURI().getPath();
        boolean isPublicPath = path.startsWith("/api/v1/auth/login") 
                || path.startsWith("/api/v1/auth/register") 
                || path.startsWith("/api/v1/auth/refresh") 
                || path.startsWith("/api/v1/auth/verify")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");

        // Mutate request to add Correlation ID and Gateway Secret
        ServerHttpRequest.Builder mutatedRequestBuilder = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .header("X-Gateway-Secret", gatewaySecret);

        if (isPublicPath) {
            // Forward public path directly
            return chain.filter(exchange.mutate().request(mutatedRequestBuilder.build()).build());
        }

        // 2. Validate JWT for protected paths
        String authHeader = request.getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Blocked request to: {} - Missing authorization header", path);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
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
                return response.writeWith(Mono.just(response.bufferFactory().wrap(
                        "{\"success\":false,\"message\":\"Access Unauthorized: Token expired.\"}"
                        .getBytes(StandardCharsets.UTF_8))));
            }

            // Extract claims
            String userId = (String) claims.get("id");
            String role = (String) claims.get("role");
            String email = claims.getSubject();

            // Mutate request headers with propagated claims
            mutatedRequestBuilder
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", role)
                    .header("X-User-Email", email);

            return chain.filter(exchange.mutate().request(mutatedRequestBuilder.build()).build());

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"success\":false,\"message\":\"Access Unauthorized: Token expired.\"}"
                    .getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(
                    "{\"success\":false,\"message\":\"Access Unauthorized: Token validation failed.\"}"
                    .getBytes(StandardCharsets.UTF_8))));
        }
    }
}
