package com.bytevault.common.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String correlationId = httpRequest.getHeader(CorrelationIdUtil.CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = httpRequest.getHeader("X-Correlation-Id");
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = httpRequest.getHeader("x-correlation-id");
            }
            CorrelationIdUtil.setCorrelationId(correlationId);
            
            // Set header on response
            httpResponse.setHeader(CorrelationIdUtil.CORRELATION_ID_HEADER, CorrelationIdUtil.getCorrelationId());
        }
        
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationIdUtil.clear();
        }
    }
}
