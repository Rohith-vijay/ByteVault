package com.bytevault.common.util;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class FeignCorrelationInterceptor implements RequestInterceptor {

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = CorrelationIdUtil.getCorrelationId();
        if (correlationId != null) {
            template.header(CorrelationIdUtil.CORRELATION_ID_HEADER, correlationId);
        }

        if (gatewaySecret != null && !gatewaySecret.isEmpty()) {
            if (!template.headers().containsKey("X-Gateway-Secret")) {
                template.header("X-Gateway-Secret", gatewaySecret);
            }
        }

        if (!template.headers().containsKey("X-User-Roles")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
                String roles = auth.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("ROLE_INTERNAL_SERVICE");
                template.header("X-User-Roles", roles);
            } else {
                template.header("X-User-Roles", "ROLE_INTERNAL_SERVICE");
            }
        }

        if (!template.headers().containsKey("X-User-Id")) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && !auth.getName().isEmpty()) {
                template.header("X-User-Id", auth.getName());
            } else {
                template.header("X-User-Id", "00000000-0000-0000-0000-000000000000");
            }
        }
    }
}
