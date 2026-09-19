package com.bytevault.warehouse.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickListResponseDto {
    private UUID id;
    private UUID orderId;
    private String warehouseCode;
    private String status;
    private UUID assignedStaffId;
    private String notes;
    private LocalDateTime createdAt;
    private List<PickListItemDto> items;
}
