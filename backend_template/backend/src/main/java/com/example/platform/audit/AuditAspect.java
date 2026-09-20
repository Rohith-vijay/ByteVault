package com.example.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        String actionName = auditAction.value();
        String performedBy = "anonymous";
        
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
            SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            performedBy = SecurityContextHolder.getContext().getAuthentication().getName();
        }

        String targetResource = "Unknown";
        try {
            String className = joinPoint.getTarget().getClass().getSimpleName();
            targetResource = className.replace("Controller", "").replace("Service", "").replace("Aspect", "");
        } catch (Exception e) {
            log.warn("Failed to resolve target resource name from class signature: {}", e.getMessage());
        }

        String ipAddress = "127.0.0.1";
        String userAgent = "System/Unknown";
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            userAgent = request.getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 497) + "...";
            }
            
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader != null && !xfHeader.trim().isEmpty()) {
                ipAddress = xfHeader.split(",")[0].trim();
            } else {
                ipAddress = request.getRemoteAddr();
            }
        }

        String details = "Method invoked: " + joinPoint.getSignature().toShortString();
        try {
            Object[] args = joinPoint.getArgs();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            StringBuilder sb = new StringBuilder("Parameters: ");
            for (int i = 0; i < args.length; i++) {
                if (paramNames != null && i < paramNames.length) {
                    String name = paramNames[i];
                    if (name.equalsIgnoreCase("password") || name.equalsIgnoreCase("token") || name.equalsIgnoreCase("secret")) {
                        sb.append(name).append("=[MASKED]; ");
                    } else if (args[i] instanceof org.springframework.web.multipart.MultipartFile) {
                        String filename = ((org.springframework.web.multipart.MultipartFile) args[i]).getOriginalFilename();
                        sb.append(name).append("=").append(filename).append("; ");
                    } else if (args[i] instanceof jakarta.servlet.ServletRequest || args[i] instanceof jakarta.servlet.ServletResponse) {
                        sb.append(name).append("=[ServletObject]; ");
                    } else if (args[i] instanceof org.springframework.security.core.Authentication) {
                        sb.append(name).append("=").append(((org.springframework.security.core.Authentication) args[i]).getName()).append("; ");
                    } else {
                        String valueStr = args[i] != null ? objectMapper.writeValueAsString(args[i]) : "null";
                        sb.append(name).append("=").append(sanitize(valueStr)).append("; ");
                    }
                }
            }
            details = sb.toString();
        } catch (Exception e) {
            log.warn("Failed to serialize arguments for audit log, fallback to short signature. Error: {}", e.getMessage());
        }

        Object result;
        try {
            result = joinPoint.proceed();
            
            auditService.log(actionName, performedBy, targetResource, details, ipAddress, userAgent, "SUCCESS", null);
            return result;
        } catch (Throwable throwable) {
            String errorMsg = throwable.getMessage();
            if (errorMsg != null && errorMsg.length() > 2000) {
                errorMsg = errorMsg.substring(0, 1997) + "...";
            }
            
            auditService.log(actionName, performedBy, targetResource, details, ipAddress, userAgent, "FAILED", errorMsg);
            throw throwable;
        }
    }

    private String sanitize(String input) {
        if (input == null) return null;
        String masked = input.replaceAll("(?i)(\"(?:password|passwordConfirm|token|secret|refreshToken|mailPassword|razorpaySecret)\"\\s*:\\s*\")[^\"]+(\")", "$1[MASKED]$2");
        masked = masked.replaceAll("(?i)(\"(?:password|passwordConfirm|token|secret|refreshToken|mailPassword|razorpaySecret)\"\\s*:\\s*)[^,}\\s]+", "$1\"[MASKED]\"");
        return masked;
    }
}
