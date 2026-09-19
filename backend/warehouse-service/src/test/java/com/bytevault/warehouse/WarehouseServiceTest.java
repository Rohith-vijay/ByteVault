package com.bytevault.warehouse;

import com.bytevault.warehouse.entity.WarehouseLocation;
import com.bytevault.warehouse.repository.WarehouseRepository;
import com.bytevault.warehouse.service.WarehouseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    @DisplayName("Assign warehouse location for physical product")
    void testAssignLocation() {
        UUID productId = UUID.randomUUID();
        when(warehouseRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(warehouseRepository.save(any(WarehouseLocation.class))).thenAnswer(i -> i.getArgument(0));

        WarehouseLocation location = warehouseService.assignLocation(productId, "WH-WEST", "B4", "12");

        assertNotNull(location);
        assertEquals(productId, location.getProductId());
        assertEquals("WH-WEST", location.getWarehouseCode());
        assertEquals("B4", location.getShelf());
        assertEquals("12", location.getBin());
    }

    @Test
    @DisplayName("Get default warehouse location when not explicitly set")
    void testGetLocation_Default() {
        UUID productId = UUID.randomUUID();
        when(warehouseRepository.findByProductId(productId)).thenReturn(Optional.empty());

        WarehouseLocation location = warehouseService.getLocation(productId);

        assertNotNull(location);
        assertEquals("WH-MAIN", location.getWarehouseCode());
        assertEquals("A1", location.getShelf());
    }

    @Test
    @DisplayName("Update existing warehouse location reuses existing entity")
    void testAssignLocation_UpdateExisting() {
        UUID productId = UUID.randomUUID();
        WarehouseLocation existing = WarehouseLocation.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .warehouseCode("WH-OLD")
                .shelf("A1")
                .bin("01")
                .build();

        when(warehouseRepository.findByProductId(productId)).thenReturn(Optional.of(existing));
        when(warehouseRepository.save(any(WarehouseLocation.class))).thenAnswer(i -> i.getArgument(0));

        WarehouseLocation updated = warehouseService.assignLocation(productId, "WH-NEW", "C3", "99");

        assertNotNull(updated);
        assertEquals("WH-NEW", updated.getWarehouseCode());
        assertEquals("C3", updated.getShelf());
        assertEquals("99", updated.getBin());
    }
}
