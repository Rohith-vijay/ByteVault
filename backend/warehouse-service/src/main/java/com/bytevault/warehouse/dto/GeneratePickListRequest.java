package com.bytevault.warehouse.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratePickListRequest {
    @NotNull(message = "Order ID is required")
    private UUID orderId;

    private String warehouseCode;

    private UUID assignedStaffId;

    private String notes;

    @NotEmpty(message = "Items list cannot be empty")
    private List<PickListItemDto> items;
}
