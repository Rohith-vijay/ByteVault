package com.bytevault.warehouse.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseLocationResponseDto {
    private UUID id;
    private UUID productId;
    private String warehouseCode;
    private String zone;
    private String aisle;
    private String shelf;
    private String bin;
    private Integer capacity;
    private Integer currentStock;
}
