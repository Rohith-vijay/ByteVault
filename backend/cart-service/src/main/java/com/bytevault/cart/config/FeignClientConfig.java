package com.bytevault.cart.config;

import com.bytevault.common.util.CorrelationIdUtil;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.stream.Collectors;

/**
 * Feign Client Configuration for internal service-to-service communication.
 * Strictly adheres to ByteVault perimeter security:
 * - Injects X-Gateway-Secret to pass DownstreamSecurityFilter verification.
 * - Propagates actual authenticated user identity when present in SecurityContext.
 * - Never fabricates fake user IDs when unauthenticated.
 */
@Configuration
public class FeignClientConfig {

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor feignSecurityAndCorrelationInterceptor() {
        return template -> {
            // 1. Mandatory Gateway Secret for downstream service perimeter verification
            if (gatewaySecret != null && !gatewaySecret.isEmpty()) {
                template.header("X-Gateway-Secret", gatewaySecret);
            }

            // 2. Correlation ID propagation for distributed tracing
            String correlationId = CorrelationIdUtil.getCorrelationId();
            if (correlationId != null && !correlationId.isEmpty()) {
                template.header(CorrelationIdUtil.CORRELATION_ID_HEADER, correlationId);
            }

            // 3. Propagate actual authenticated user identity if present in SecurityContext
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getName() != null
                    && !"anonymousUser".equalsIgnoreCase(auth.getName())) {

                template.header("X-User-Id", auth.getName());

                if (auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                    String roles = auth.getAuthorities().stream()
                            .map(a -> a.getAuthority())
                            .collect(Collectors.joining(","));
                    template.header("X-User-Roles", roles);
                } else {
                    template.header("X-User-Roles", "ROLE_CUSTOMER");
                }
            } else {
                // Internal service invocation without an authenticated user context
                template.header("X-User-Roles", "ROLE_INTERNAL_SERVICE");
                // Do NOT send or fabricate an X-User-Id header
            }
        };
    }
}
