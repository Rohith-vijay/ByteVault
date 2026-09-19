package com.bytevault.common.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DownstreamSecurityFilter implements Filter {

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String USER_EMAIL_HEADER = "X-User-Email";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String incomingSecret = httpRequest.getHeader(GATEWAY_SECRET_HEADER);
            
            String uri = httpRequest.getRequestURI();
            // Allow direct access to Swagger UI, OpenAPI docs, Actuator, and signed download streaming
            if (uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs") || uri.startsWith("/actuator") || uri.contains("swagger") || uri.contains("api-docs") || uri.startsWith("/api/v1/products/assets/download")) {
                chain.doFilter(request, response);
                return;
            }

            // Block direct external access bypassing API Gateway
            if (incomingSecret == null || !incomingSecret.equals(gatewaySecret)) {
                log.warn("Blocked direct access attempt to: {}. Invalid gateway secret.", httpRequest.getRequestURI());
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"success\":false,\"message\":\"Access Forbidden: Direct calls not allowed.\"}");
                return;
            }

            // Extract claims
            String userId = httpRequest.getHeader(USER_ID_HEADER);
            String rolesStr = httpRequest.getHeader(USER_ROLES_HEADER);
            String email = httpRequest.getHeader(USER_EMAIL_HEADER);

            if (userId != null && rolesStr != null) {
                try {
                    List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesStr.split(","))
                            .map(role -> {
                                String cleanRole = role.trim();
                                if (!cleanRole.startsWith("ROLE_")) {
                                    cleanRole = "ROLE_" + cleanRole;
                                }
                                return new SimpleGrantedAuthority(cleanRole);
                            })
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            email != null ? email : userId,
                            null,
                            authorities
                    );
                    
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    log.error("Failed to set security context from forwarded gateway headers", e);
                }
            }
        }

        chain.doFilter(request, response);
    }
}
