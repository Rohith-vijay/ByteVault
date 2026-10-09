package com.bytevault.product.config;

import com.bytevault.common.util.CorrelationIdUtil;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign client configuration for product-service's inter-service calls
 * (currently: UserClient -> identity-service for vendor status checks).
 * Propagates X-Gateway-Secret, X-User-Roles, X-User-Id, and X-Correlation-ID
 * for full perimeter security compliance and distributed tracing.
 */
@Configuration
public class FeignClientConfig {

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor feignGatewaySecretInterceptor() {
        return template -> {
            template.header("X-Gateway-Secret", gatewaySecret);
            template.header("X-User-Roles", "ROLE_INTERNAL_SERVICE");
            template.header("X-User-Id", "00000000-0000-0000-0000-000000000000");
            // Propagate distributed trace correlation ID
            String correlationId = CorrelationIdUtil.getCorrelationId();
            if (correlationId != null && !correlationId.isEmpty()) {
                template.header(CorrelationIdUtil.CORRELATION_ID_HEADER, correlationId);
            }
        };
    }
}
