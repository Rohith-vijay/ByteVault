package com.bytevault.shipping.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingResponseDto {
    private String trackingNumber;
    private UUID orderId;
    private String carrier;
    private String status;
    private String destinationSummary;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private List<ShipmentHistoryDto> milestones;
}
