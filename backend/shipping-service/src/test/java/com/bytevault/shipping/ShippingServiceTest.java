package com.bytevault.shipping;

import com.bytevault.shipping.entity.Shipment;
import com.bytevault.shipping.event.ShippingEventPublisher;
import com.bytevault.shipping.repository.ShipmentHistoryRepository;
import com.bytevault.shipping.repository.ShipmentRepository;
import com.bytevault.shipping.service.ShipmentService;
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
public class ShippingServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentHistoryRepository shipmentHistoryRepository;

    @Mock
    private ShippingEventPublisher shippingEventPublisher;

    @InjectMocks
    private ShipmentService shipmentService;

    @Test
    @DisplayName("Create shipment with generated tracking number")
    void testCreateShipment() {
        UUID orderId = UUID.randomUUID();
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));

        Shipment shipment = shipmentService.createShipment(orderId, "100 Tech Blvd", "FedEx Express");

        assertNotNull(shipment);
        assertEquals(orderId, shipment.getOrderId());
        assertEquals("FedEx Express", shipment.getCarrier());
        assertEquals("PENDING", shipment.getStatus());
        assertTrue(shipment.getTrackingNumber().startsWith("TRACK-"));
    }

    @Test
    @DisplayName("Retrieve shipment by orderId")
    void testGetShipmentByOrderId() {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .orderId(orderId)
                .trackingNumber("TRACK-999")
                .carrier("DHL")
                .build();

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(shipment));

        Shipment result = shipmentService.getShipmentByOrderId(orderId);

        assertNotNull(result);
        assertEquals("TRACK-999", result.getTrackingNumber());
    }
}
