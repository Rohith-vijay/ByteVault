package com.bytevault.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    private Integer quantity; // New absolute quantity or adjustment
    private Integer quantityAdjustment; // Relative +/- adjustment
    private Boolean isDigital;
    private Boolean isAvailable;
}
