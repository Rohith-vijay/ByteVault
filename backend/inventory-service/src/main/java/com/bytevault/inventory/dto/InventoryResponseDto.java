package com.bytevault.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponseDto {
    private UUID id;
    private UUID productId;
    private Boolean isDigital;
    private Boolean isAvailable;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
}
