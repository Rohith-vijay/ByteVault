package com.bytevault.inventory;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.inventory.controller.InventoryController;
import com.bytevault.inventory.dto.InventoryResponseDto;
import com.bytevault.inventory.dto.StockAdjustmentRequest;
import com.bytevault.inventory.dto.StockReservationItemDto;
import com.bytevault.inventory.dto.StockReservationRequest;
import com.bytevault.inventory.dto.StockReservationResponse;
import com.bytevault.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InventoryEndpointTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    @Test
    @DisplayName("GET /api/v1/inventory/{productId} returns inventory status DTO")
    void testGetInventoryEndpoint() {
        UUID productId = UUID.randomUUID();
        InventoryResponseDto dto = InventoryResponseDto.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .build();

        when(inventoryService.getInventoryDto(productId)).thenReturn(dto);

        ResponseEntity<ApiResponse<InventoryResponseDto>> response = inventoryController.getInventory(productId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertEquals(100, response.getBody().data().getQuantity());
        assertEquals(90, response.getBody().data().getAvailableQuantity());
    }

    @Test
    @DisplayName("POST /api/v1/inventory/reserve with query params succeeds")
    void testReserveQueryParams() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(inventoryService.reserveStock(any(UUID.class), any(UUID.class), any(Integer.class))).thenReturn(true);

        ResponseEntity<ApiResponse<Object>> response = inventoryController.reserveStock(null, productId, 2, orderId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        Map<?, ?> data = (Map<?, ?>) response.getBody().data();
        assertTrue((Boolean) data.get("reserved"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/reserve with JSON body reserves order batch")
    void testReserveJsonBatch() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        StockReservationRequest req = StockReservationRequest.builder()
                .orderId(orderId)
                .items(List.of(StockReservationItemDto.builder().productId(productId).quantity(3).build()))
                .build();

        StockReservationResponse res = StockReservationResponse.builder()
                .orderId(orderId)
                .reserved(true)
                .message("All items reserved successfully")
                .build();

        when(inventoryService.reserveStockForOrder(any(StockReservationRequest.class))).thenReturn(res);

        ResponseEntity<ApiResponse<Object>> response = inventoryController.reserveStock(req, null, null, null);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        StockReservationResponse bodyData = (StockReservationResponse) response.getBody().data();
        assertTrue(bodyData.isReserved());
    }

    @Test
    @DisplayName("POST /api/v1/inventory/release with orderId releases stock")
    void testReleaseEndpoint() {
        UUID orderId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = inventoryController.releaseStock(orderId, null, 1);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertTrue((Boolean) response.getBody().data().get("released"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/commit with orderId commits stock")
    void testCommitEndpoint() {
        UUID orderId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = inventoryController.commitStock(orderId);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertTrue((Boolean) response.getBody().data().get("committed"));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/adjust adjusts stock level")
    void testAdjustEndpoint() {
        UUID productId = UUID.randomUUID();
        StockAdjustmentRequest req = StockAdjustmentRequest.builder()
                .productId(productId)
                .quantity(50)
                .build();

        InventoryResponseDto dto = InventoryResponseDto.builder()
                .productId(productId)
                .quantity(50)
                .reservedQuantity(0)
                .availableQuantity(50)
                .build();

        when(inventoryService.adjustStock(any(StockAdjustmentRequest.class))).thenReturn(dto);

        ResponseEntity<ApiResponse<InventoryResponseDto>> response = inventoryController.adjustStock(req);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertEquals(50, response.getBody().data().getQuantity());
    }
}
