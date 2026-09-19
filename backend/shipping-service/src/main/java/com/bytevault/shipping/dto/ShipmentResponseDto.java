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
public class ShipmentResponseDto {
    private UUID id;
    private UUID orderId;
    private UUID customerId;
    private UUID vendorId;
    private String trackingNumber;
    private String carrier;
    private String status;
    private String shippingAddress;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private Double weightKg;
    private String notes;
    private List<ShipmentHistoryDto> milestones;
}
