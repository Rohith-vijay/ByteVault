package com.bytevault.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentStatusUpdateRequest {
    @NotBlank(message = "Status is required")
    private String status;
    private String location;
    private String remarks;
}
