package com.bytevault.shipping.integration;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.shipping.entity.Shipment;
import com.bytevault.shipping.event.ShippingEventPublisher;
import com.bytevault.shipping.repository.ShipmentHistoryRepository;
import com.bytevault.shipping.repository.ShipmentRepository;
import com.bytevault.shipping.service.ShipmentService;
import org.junit.jupiter.api.BeforeEach;
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
public class PhysicalCommerceEndToEndWorkflowTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentHistoryRepository shipmentHistoryRepository;

    @Mock
    private ShippingEventPublisher shippingEventPublisher;

    @InjectMocks
    private ShipmentService shipmentService;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Physical order lifecycle: Creation -> Tracking Generation -> Manifested -> In Transit -> Delivered")
    void testPhysicalCommerceLifecycle() {
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> {
            Shipment s = i.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        // Step 1: Create Shipment
        Shipment created = shipmentService.createShipment(orderId, "742 Evergreen Terrace, Springfield", "DHL Express");
        assertNotNull(created);
        assertEquals("PENDING", created.getStatus());
        assertNotNull(created.getTrackingNumber());
        assertTrue(created.getTrackingNumber().startsWith("TRACK-DHL-"));

        // Mock DB find for subsequent transitions
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(created));

        // Step 2: Manifested
        Shipment manifested = shipmentService.updateShipmentStatus(orderId, "MANIFESTED");
        assertEquals("MANIFESTED", manifested.getStatus());

        // Step 3: Shipped
        Shipment shipped = shipmentService.updateShipmentStatus(orderId, "SHIPPED");
        assertEquals("SHIPPED", shipped.getStatus());

        // Step 4: In Transit
        Shipment inTransit = shipmentService.updateShipmentStatus(orderId, "IN_TRANSIT");
        assertEquals("IN_TRANSIT", inTransit.getStatus());

        // Step 5: Delivered
        Shipment delivered = shipmentService.updateShipmentStatus(orderId, "DELIVERED");
        assertEquals("DELIVERED", delivered.getStatus());

        // Step 6: Illegal Transition from Terminal State
        assertThrows(BadRequestException.class, () ->
                shipmentService.updateShipmentStatus(orderId, "PENDING"));
    }

    @Test
    @DisplayName("Tracking number lookup returns matching shipment")
    void testTrackingNumberLookup() {
        String trackingNum = "TRACK-BV-987654";
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber(trackingNum)
                .carrier("FedEx")
                .status("SHIPPED")
                .build();

        when(shipmentRepository.findByTrackingNumber(trackingNum)).thenReturn(Optional.of(shipment));

        Shipment found = shipmentService.getShipmentByTrackingNumber(trackingNum);

        assertNotNull(found);
        assertEquals(orderId, found.getOrderId());
        assertEquals("SHIPPED", found.getStatus());
    }
}
