package com.bytevault.order.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        };
    }
}
