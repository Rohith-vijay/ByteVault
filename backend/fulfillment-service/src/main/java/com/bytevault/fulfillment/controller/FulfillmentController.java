package com.bytevault.fulfillment.controller;

import com.bytevault.fulfillment.service.FulfillmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    @GetMapping("/downloads/{productId}")
    public ResponseEntity<Map<String, String>> downloadDigitalProduct(
            @PathVariable UUID productId,
            @RequestHeader("X-User-Id") String userIdHeader,
            HttpServletRequest request) {

        UUID userId = UUID.fromString(userIdHeader);
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        log.info("[FulfillmentController] Download requested by user={} for product={}", userId, productId);
        String downloadUrl = fulfillmentService.requestSecureDownload(userId, productId, ipAddress, userAgent);

        Map<String, String> response = new HashMap<>();
        response.put("downloadUrl", downloadUrl);
        return ResponseEntity.ok(response);
    }
}
