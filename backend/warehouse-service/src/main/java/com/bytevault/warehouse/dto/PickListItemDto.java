package com.bytevault.warehouse.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickListItemDto {
    private UUID id;
    private UUID productId;
    private String sku;
    private String productTitle;
    private Integer quantity;
    private String warehouseCode;
    private String zone;
    private String aisle;
    private String shelf;
    private String bin;
    private Boolean isPicked;
}
