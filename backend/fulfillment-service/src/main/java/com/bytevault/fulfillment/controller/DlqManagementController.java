package com.bytevault.fulfillment.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.fulfillment.config.RabbitConfig;
import com.bytevault.fulfillment.dto.DlqIncidentDto;
import com.bytevault.fulfillment.service.DlqIncidentRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/fulfillment/internal/dlq")
@RequiredArgsConstructor
public class DlqManagementController {

    private final DlqIncidentRegistry dlqIncidentRegistry;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<List<DlqIncidentDto>>> getDlqIncidents(
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            Authentication auth) {

        if (!isAuthorized(gatewaySecretHeader, userRolesHeader, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access Forbidden: Internal service or admin authentication required", 403));
        }

        return ResponseEntity.ok(ApiResponse.success("DLQ incidents retrieved", dlqIncidentRegistry.getAllIncidents()));
    }

    @PostMapping("/replay/{incidentId}")
    public ResponseEntity<ApiResponse<String>> replayDlqIncident(
            @PathVariable String incidentId,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecretHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String userRolesHeader,
            Authentication auth) {

        if (!isAuthorized(gatewaySecretHeader, userRolesHeader, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Access Forbidden: Internal service or admin authentication required", 403));
        }

        Optional<DlqIncidentDto> opt = dlqIncidentRegistry.getIncident(incidentId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("DLQ incident not found: " + incidentId, 404));
        }

        DlqIncidentDto incident = opt.get();
        Map<String, Object> payload = incident.getSanitizedPayload();

        log.info("[DlqManagementController] Replaying DLQ incident id={}, orderId={}", incidentId, incident.getOrderId());

        // Re-publish to main order.exchange with order.paid routing key
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, payload);

        dlqIncidentRegistry.markReplayed(incidentId);

        return ResponseEntity.ok(ApiResponse.success("Message replayed to order.paid queue successfully", incidentId));
    }

    private boolean isAuthorized(String gatewaySecretHeader, String userRolesHeader, Authentication auth) {
        boolean validSecret = gatewaySecretHeader != null && gatewaySecretHeader.equals(gatewaySecret);
        boolean hasInternalRole = (userRolesHeader != null && userRolesHeader.contains("ROLE_INTERNAL_SERVICE"))
                || (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL_SERVICE")));
        boolean hasAdminRole = (userRolesHeader != null && userRolesHeader.contains("ROLE_ADMIN"))
                || (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        return validSecret || hasInternalRole || hasAdminRole;
    }
}
