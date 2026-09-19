package com.bytevault.fulfillment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DlqIncidentDto {
    private String incidentId;
    private String orderId;
    private String routingKey;
    private String reason;
    private int deathCount;
    private LocalDateTime timestamp;
    private Map<String, Object> sanitizedPayload;
    private String status; // DEAD_LETTERED, REPLAYED
}
