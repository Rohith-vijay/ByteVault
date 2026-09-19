package com.bytevault.inventory.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.inventory.dto.*;
import com.bytevault.inventory.entity.InventoryItem;
import com.bytevault.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> getInventory(@PathVariable UUID productId) {
        InventoryResponseDto dto = inventoryService.getInventoryDto(productId);
        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved", dto));
    }

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<Object>> reserveStock(
            @RequestBody(required = false) StockReservationRequest request,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) UUID orderId) {

        if (request != null && request.getItems() != null && !request.getItems().isEmpty()) {
            StockReservationResponse response = inventoryService.reserveStockForOrder(request);
            if (response.isReserved()) {
                return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(response.getMessage(), 400));
            }
        }

        if (productId == null || quantity == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing productId or quantity", 400));
        }

        UUID targetOrderId = orderId != null ? orderId : UUID.randomUUID();
        boolean reserved = inventoryService.reserveStock(targetOrderId, productId, quantity);
        if (reserved) {
            return ResponseEntity.ok(ApiResponse.success("Stock reserved successfully",
                    Map.of("reserved", true, "productId", productId, "quantity", quantity, "orderId", targetOrderId)));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Insufficient stock", 400));
        }
    }

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Map<String, Object>>> releaseStock(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false, defaultValue = "1") int quantity) {

        if (orderId != null && productId == null) {
            inventoryService.releaseStockForOrder(orderId);
            return ResponseEntity.ok(ApiResponse.success("Stock released for order", Map.of("released", true, "orderId", orderId)));
        }

        if (productId != null) {
            inventoryService.releaseStock(orderId, productId, quantity);
            return ResponseEntity.ok(ApiResponse.success("Stock released",
                    Map.of("released", true, "productId", productId, "quantity", quantity)));
        }

        return ResponseEntity.badRequest().body(ApiResponse.error("Must provide orderId or productId to release stock", 400));
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commitStock(@RequestParam UUID orderId) {
        inventoryService.commitStockForOrder(orderId);
        return ResponseEntity.ok(ApiResponse.success("Stock committed for order", Map.of("committed", true, "orderId", orderId)));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'VENDOR')")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        InventoryResponseDto dto = inventoryService.adjustStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", dto));
    }

    @PutMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'VENDOR')")
    public ResponseEntity<ApiResponse<InventoryResponseDto>> adjustStockPut(@Valid @RequestBody StockAdjustmentRequest request) {
        InventoryResponseDto dto = inventoryService.adjustStock(request);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", dto));
    }
}
