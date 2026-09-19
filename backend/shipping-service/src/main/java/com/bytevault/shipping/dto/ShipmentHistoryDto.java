package com.bytevault.shipping.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentHistoryDto {
    private UUID id;
    private String status;
    private String location;
    private String remarks;
    private LocalDateTime timestamp;
}
