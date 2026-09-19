package com.bytevault.inventory;

import com.bytevault.inventory.dto.StockAdjustmentRequest;
import com.bytevault.inventory.dto.StockReservationItemDto;
import com.bytevault.inventory.dto.StockReservationRequest;
import com.bytevault.inventory.dto.StockReservationResponse;
import com.bytevault.inventory.entity.InventoryItem;
import com.bytevault.inventory.entity.InventoryReservation;
import com.bytevault.inventory.repository.InventoryRepository;
import com.bytevault.inventory.repository.InventoryReservationRepository;
import com.bytevault.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("Digital product inventory policy -> Always available with infinite virtual quantity")
    void testDigitalProductInventory() {
        UUID productId = UUID.randomUUID();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));

        InventoryItem item = inventoryService.getInventory(productId);

        assertNotNull(item);
        assertTrue(item.getIsDigital());
        assertTrue(item.getIsAvailable());
        assertEquals(999999, item.getAvailableQuantity());
    }

    @Test
    @DisplayName("Digital product reservation succeeds without locking physical stock")
    void testDigitalProductReservation_Success() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem digitalItem = InventoryItem.builder()
                .productId(productId)
                .isDigital(true)
                .isAvailable(true)
                .quantity(999999)
                .reservedQuantity(0)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(digitalItem));

        boolean reserved = inventoryService.reserveStock(orderId, productId, 1);

        assertTrue(reserved);
        verify(inventoryRepository, never()).atomicReserveStock(any(), anyInt());
    }

    @Test
    @DisplayName("Physical product stock reservation via atomic DB lock succeeds and records reservation")
    void testPhysicalProductReservation_Success() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem physicalItem = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(50)
                .reservedQuantity(10)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(physicalItem));
        when(inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId)).thenReturn(Optional.empty());
        when(inventoryRepository.atomicReserveStock(eq(productId), eq(5))).thenReturn(1);

        boolean reserved = inventoryService.reserveStock(orderId, productId, 5);

        assertTrue(reserved);
        verify(inventoryRepository, times(1)).atomicReserveStock(productId, 5);
        verify(inventoryReservationRepository, times(1)).save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("Physical product stock reservation fails on insufficient stock")
    void testPhysicalProductReservation_InsufficientStock() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem physicalItem = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(10)
                .reservedQuantity(10)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(physicalItem));
        when(inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId)).thenReturn(Optional.empty());
        when(inventoryRepository.atomicReserveStock(eq(productId), eq(5))).thenReturn(0);

        boolean reserved = inventoryService.reserveStock(orderId, productId, 5);

        assertFalse(reserved);
        verify(inventoryReservationRepository, never()).save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("Idempotent stock reservation for repeated order/product call")
    void testIdempotentStockReservation() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem physicalItem = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(50)
                .reservedQuantity(10)
                .build();

        InventoryReservation existing = InventoryReservation.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(5)
                .status("RESERVED")
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(physicalItem));
        when(inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId)).thenReturn(Optional.of(existing));

        boolean reserved = inventoryService.reserveStock(orderId, productId, 5);

        assertTrue(reserved);
        verify(inventoryRepository, never()).atomicReserveStock(any(), anyInt());
    }

    @Test
    @DisplayName("Concurrent duplicate reservation race condition recovers idempotently via DataIntegrityViolationException")
    void testConcurrentDuplicateReservationRecovery() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem physicalItem = InventoryItem.builder()
                .productId(productId)
                .isDigital(false)
                .isAvailable(true)
                .quantity(50)
                .reservedQuantity(10)
                .build();

        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(physicalItem));
        when(inventoryReservationRepository.findByOrderIdAndProductId(orderId, productId)).thenReturn(Optional.empty());
        when(inventoryRepository.atomicReserveStock(eq(productId), eq(2))).thenReturn(1);
        when(inventoryReservationRepository.save(any(InventoryReservation.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate reservation"));

        boolean reserved = inventoryService.reserveStock(orderId, productId, 2);

        assertTrue(reserved);
    }

    @Test
    @DisplayName("Batch order reservation rolls back partial reservations if one item fails")
    void testBatchOrderReservation_RollbackOnFailure() {
        UUID orderId = UUID.randomUUID();
        UUID prod1 = UUID.randomUUID();
        UUID prod2 = UUID.randomUUID();

        InventoryItem item1 = InventoryItem.builder().productId(prod1).isDigital(false).isAvailable(true).quantity(10).reservedQuantity(0).build();
        InventoryItem item2 = InventoryItem.builder().productId(prod2).isDigital(false).isAvailable(true).quantity(2).reservedQuantity(2).build();

        when(inventoryRepository.findByProductId(prod1)).thenReturn(Optional.of(item1));
        when(inventoryRepository.findByProductId(prod2)).thenReturn(Optional.of(item2));

        when(inventoryReservationRepository.findByOrderIdAndProductId(orderId, prod1)).thenReturn(Optional.empty());
        when(inventoryReservationRepository.findByOrderIdAndProductId(orderId, prod2)).thenReturn(Optional.empty());

        when(inventoryRepository.atomicReserveStock(eq(prod1), eq(2))).thenReturn(1);
        when(inventoryRepository.atomicReserveStock(eq(prod2), eq(5))).thenReturn(0); // Insufficient stock for item 2

        StockReservationRequest request = StockReservationRequest.builder()
                .orderId(orderId)
                .items(List.of(
                        StockReservationItemDto.builder().productId(prod1).quantity(2).build(),
                        StockReservationItemDto.builder().productId(prod2).quantity(5).build()
                ))
                .build();

        StockReservationResponse response = inventoryService.reserveStockForOrder(request);

        assertFalse(response.isReserved());
        // Verify item1 reservation was rolled back
        verify(inventoryRepository, times(1)).atomicReleaseStock(eq(prod1), eq(2));
    }

    @Test
    @DisplayName("Releasing stock for cancelled order safely decrements reserved quantity")
    void testReleaseStockForOrder() {
        UUID orderId = UUID.randomUUID();
        UUID prodId = UUID.randomUUID();

        InventoryItem item = InventoryItem.builder().productId(prodId).isDigital(false).isAvailable(true).quantity(20).reservedQuantity(5).build();
        InventoryReservation res = InventoryReservation.builder().orderId(orderId).productId(prodId).quantity(5).status("RESERVED").build();

        when(inventoryReservationRepository.findByOrderIdAndStatus(orderId, "RESERVED")).thenReturn(List.of(res));
        when(inventoryRepository.findByProductId(prodId)).thenReturn(Optional.of(item));

        inventoryService.releaseStockForOrder(orderId);

        verify(inventoryRepository, times(1)).atomicReleaseStock(prodId, 5);
        assertEquals("RELEASED", res.getStatus());
        verify(inventoryReservationRepository, times(1)).save(res);
    }

    @Test
    @DisplayName("Commit stock for paid order permanently decrements physical and reserved stock")
    void testCommitStockForOrder() {
        UUID orderId = UUID.randomUUID();
        UUID prodId = UUID.randomUUID();

        InventoryItem item = InventoryItem.builder().productId(prodId).isDigital(false).isAvailable(true).quantity(20).reservedQuantity(5).build();
        InventoryReservation res = InventoryReservation.builder().orderId(orderId).productId(prodId).quantity(5).status("RESERVED").build();

        when(inventoryReservationRepository.findByOrderIdAndStatus(orderId, "RESERVED")).thenReturn(List.of(res));
        when(inventoryRepository.findByProductId(prodId)).thenReturn(Optional.of(item));

        inventoryService.commitStockForOrder(orderId);

        verify(inventoryRepository, times(1)).atomicCommitStock(prodId, 5);
        assertEquals("COMMITTED", res.getStatus());
        verify(inventoryReservationRepository, times(1)).save(res);
    }

    @Test
    @DisplayName("Adjust stock overrides quantity, digital flag, and availability")
    void testAdjustStock() {
        UUID prodId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder().productId(prodId).isDigital(false).isAvailable(true).quantity(10).reservedQuantity(2).build();

        when(inventoryRepository.findByProductId(prodId)).thenReturn(Optional.of(item));
        when(inventoryRepository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));

        StockAdjustmentRequest request = StockAdjustmentRequest.builder()
                .productId(prodId)
                .quantity(100)
                .isDigital(false)
                .isAvailable(true)
                .build();

        var dto = inventoryService.adjustStock(request);

        assertNotNull(dto);
        assertEquals(100, dto.getQuantity());
        assertEquals(2, dto.getReservedQuantity());
        assertEquals(98, dto.getAvailableQuantity());
    }
}
