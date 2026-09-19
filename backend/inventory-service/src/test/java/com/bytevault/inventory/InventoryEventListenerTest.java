package com.bytevault.inventory;

import com.bytevault.inventory.event.InventoryEventListener;
import com.bytevault.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InventoryEventListenerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryEventListener inventoryEventListener;

    @Test
    @DisplayName("order.cancelled event triggers releaseStockForOrder")
    void testHandleOrderCancelled() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = Map.of("orderId", orderId.toString(), "reason", "Customer cancelled");

        inventoryEventListener.handleOrderCancelled(event);

        verify(inventoryService, times(1)).releaseStockForOrder(orderId);
    }

    @Test
    @DisplayName("payment.failed event triggers releaseStockForOrder")
    void testHandlePaymentFailed() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = Map.of("orderId", orderId.toString(), "reason", "Card declined");

        inventoryEventListener.handlePaymentFailed(event);

        verify(inventoryService, times(1)).releaseStockForOrder(orderId);
    }

    @Test
    @DisplayName("order.paid event triggers commitStockForOrder")
    void testHandleOrderPaid() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = Map.of("orderId", orderId.toString(), "status", "PAID");

        inventoryEventListener.handleOrderPaid(event);

        verify(inventoryService, times(1)).commitStockForOrder(orderId);
    }

    @Test
    @DisplayName("Malformed event with null orderId handled gracefully without exception")
    void testHandleMalformedEvent() {
        Map<String, Object> event = Map.of("foo", "bar");

        inventoryEventListener.handleOrderCancelled(event);

        verify(inventoryService, never()).releaseStockForOrder(any());
    }
}
