package com.bytevault.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationRequest {

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<StockReservationItemDto> items;
}
