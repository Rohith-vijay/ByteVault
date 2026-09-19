package com.bytevault.shipping;

import com.bytevault.shipping.event.ShippingEventListener;
import com.bytevault.shipping.service.ShipmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShippingEventListenerTest {

    @Mock
    private ShipmentService shipmentService;

    @InjectMocks
    private ShippingEventListener eventListener;

    @Test
    @DisplayName("order.paid event for PHYSICAL order provisions shipment")
    void testHandleOrderPaid_PhysicalOrder() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("orderType", "PHYSICAL");
        event.put("userId", userId.toString());
        event.put("shippingAddress", "456 Market St, San Francisco, CA");

        eventListener.handleOrderPaid(event);

        verify(shipmentService, times(1)).createOrGetShipment(
                eq(orderId), eq(userId), isNull(), eq("456 Market St, San Francisco, CA"), any(), isNull());
    }

    @Test
    @DisplayName("order.paid event for DIGITAL order skips shipment creation")
    void testHandleOrderPaid_DigitalOrder() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("orderType", "DIGITAL");

        eventListener.handleOrderPaid(event);

        verify(shipmentService, never()).createOrGetShipment(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("order.cancelled event triggers cancellation for pending shipments")
    void testHandleOrderCancelled() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());

        eventListener.handleOrderCancelled(event);

        verify(shipmentService, times(1)).cancelShipmentIfPending(eq(orderId), anyString());
    }
}
