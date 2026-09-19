package com.bytevault.inventory.dto;

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
public class StockReservationResponse {

    private UUID orderId;
    private boolean reserved;
    private String message;
    private List<ItemReservationResult> itemResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemReservationResult {
        private UUID productId;
        private int requestedQuantity;
        private boolean reserved;
        private String note;
    }
}
