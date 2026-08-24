package com.bytevault.common.util;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CorrelationIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String correlationId = httpRequest.getHeader(CorrelationIdUtil.CORRELATION_ID_HEADER);
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
