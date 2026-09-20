package com.example.platform.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${rate-limit.auth.capacity:10}")
    private int authCapacity;

    @Value("${rate-limit.auth.refill-tokens:10}")
    private int authRefillTokens;

    @Value("${rate-limit.auth.refill-seconds:60}")
    private int authRefillSeconds;

    @Value("${rate-limit.upload.capacity:3}")
    private int uploadCapacity;

    @Value("${rate-limit.upload.refill-tokens:3}")
    private int uploadRefillTokens;

    @Value("${rate-limit.upload.refill-seconds:60}")
    private int uploadRefillSeconds;

    @Value("${rate-limit.default.capacity:100}")
    private int defaultCapacity;

    @Value("${rate-limit.default.refill-tokens:100}")
    private int defaultRefillTokens;

    @Value("${rate-limit.default.refill-seconds:60}")
    private int defaultRefillSeconds;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (!isRateLimitedRoute(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String routeType = getRouteType(request);
        String ip = resolveIp(request);
        String key = ip + ":" + routeType;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucketForRoute(routeType));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: ip={}, key={}, uri={}", ip, key, uri);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"status\":429,\"title\":\"Too Many Requests\"," +
                "\"detail\":\"Max attempts exceeded for this route. Please try again later.\"}");
        }
    }

    private boolean isRateLimitedRoute(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/auth/") || uri.startsWith("/api/media/upload");
    }

    private String getRouteType(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/media/upload")) {
            return "UPLOAD";
        }
        if (uri.startsWith("/api/auth/")) {
            return "AUTH";
        }
        return "DEFAULT";
    }

    private Bucket createBucketForRoute(String routeType) {
        int cap = defaultCapacity;
        int refill = defaultRefillTokens;
        int seconds = defaultRefillSeconds;

        if ("AUTH".equals(routeType)) {
            cap = authCapacity;
            refill = authRefillTokens;
            seconds = authRefillSeconds;
        } else if ("UPLOAD".equals(routeType)) {
            cap = uploadCapacity;
            refill = uploadRefillTokens;
            seconds = uploadRefillSeconds;
        }

        return Bucket.builder()
                .addLimit(Bandwidth.classic(cap,
                        Refill.greedy(refill,
                                Duration.ofSeconds(seconds))))
                .build();
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }
}
