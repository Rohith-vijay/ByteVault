package com.bytevault.shipping.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShipmentRequest {
    @NotNull(message = "Order ID is required")
    private UUID orderId;
    private UUID customerId;
    private UUID vendorId;
    private String shippingAddress;
    private String carrier;
    private Double weightKg;
    private String notes;
}
