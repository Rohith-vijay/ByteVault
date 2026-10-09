package com.bytevault.common.config;

import com.bytevault.common.util.FeignCorrelationInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Standard reusable FeignClient configuration exported by common-library.
 * Injects X-Gateway-Secret, X-Correlation-ID, X-User-Roles, and X-User-Id
 * across all inter-service Feign calls to ensure perimeter security compliance.
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class CommonFeignConfig {

    @Bean
    @ConditionalOnMissingBean(RequestInterceptor.class)
    public RequestInterceptor feignCorrelationInterceptor() {
        return new FeignCorrelationInterceptor();
    }
}
