package com.marketplace.common.util;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeignCorrelationInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = CorrelationIdUtil.getCorrelationId();
        if (correlationId != null) {
            template.header(CorrelationIdUtil.CORRELATION_ID_HEADER, correlationId);
        }
    }
}
