package com.bytevault.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignLocationRequest {
    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotBlank(message = "Warehouse code is required")
    private String warehouseCode;

    private String zone;
    private String aisle;
    private String shelf;
    private String bin;
    private Integer capacity;
    private Integer currentStock;
}
