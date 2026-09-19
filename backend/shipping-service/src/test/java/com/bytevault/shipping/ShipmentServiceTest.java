package com.bytevault.shipping;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.shipping.dto.ShipmentResponseDto;
import com.bytevault.shipping.dto.TrackingResponseDto;
import com.bytevault.shipping.entity.Shipment;
import com.bytevault.shipping.entity.ShipmentHistory;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private ShipmentHistoryRepository shipmentHistoryRepository;

    @Mock
    private ShippingEventPublisher shippingEventPublisher;

    @InjectMocks
    private ShipmentService shipmentService;

    @Test
    @DisplayName("Create shipment generates tracking number, initial milestone, and publishes event")
    void testCreateShipment() {
        UUID orderId = UUID.randomUUID();
        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> {
            Shipment s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Shipment shipment = shipmentService.createShipment(orderId, "123 Main St, New York, NY", "FedEx");

        assertNotNull(shipment);
        assertEquals(orderId, shipment.getOrderId());
        assertEquals("PENDING", shipment.getStatus());
        assertEquals("FedEx", shipment.getCarrier());
        assertNotNull(shipment.getTrackingNumber());
        assertTrue(shipment.getTrackingNumber().startsWith("TRACK-FDX-"));
        verify(shipmentRepository, times(1)).save(any(Shipment.class));
        verify(shipmentHistoryRepository, times(1)).save(any(ShipmentHistory.class));
        verify(shippingEventPublisher, times(1)).publishShipmentCreated(any(Shipment.class));
    }

    @Test
    @DisplayName("Idempotent shipment creation returns existing shipment without duplicate inserts")
    void testCreateShipment_Idempotency() {
        UUID orderId = UUID.randomUUID();
        Shipment existing = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber("TRACK-FDX-123456")
                .carrier("FedEx")
                .status("PENDING")
                .build();

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        Shipment shipment = shipmentService.createShipment(orderId, "123 Main St, New York, NY", "FedEx");

        assertNotNull(shipment);
        assertEquals(existing.getId(), shipment.getId());
        verify(shipmentRepository, never()).save(any(Shipment.class));
        verify(shippingEventPublisher, never()).publishShipmentCreated(any(Shipment.class));
    }

    @Test
    @DisplayName("Retrieve shipment by order ID")
    void testGetShipmentByOrderId() {
        UUID orderId = UUID.randomUUID();
        Shipment existing = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber("TRACK-12345")
                .status("SHIPPED")
                .build();

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        Shipment shipment = shipmentService.getShipmentByOrderId(orderId);

        assertNotNull(shipment);
        assertEquals("TRACK-12345", shipment.getTrackingNumber());
        assertEquals("SHIPPED", shipment.getStatus());
    }

    @Test
    @DisplayName("Valid status transition succeeds: PENDING -> SHIPPED -> DELIVERED with event publishing")
    void testUpdateShipmentStatus_ValidTransitions() {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber("TRACK-999")
                .status("PENDING")
                .build();

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));

        Shipment shipped = shipmentService.updateShipmentStatus(orderId, "SHIPPED", "Sorting Center", "Package scanned");
        assertEquals("SHIPPED", shipped.getStatus());
        verify(shippingEventPublisher, times(1)).publishShipmentDispatched(any(Shipment.class));

        Shipment delivered = shipmentService.updateShipmentStatus(orderId, "DELIVERED", "Front Door", "Delivered to recipient");
        assertEquals("DELIVERED", delivered.getStatus());
        assertNotNull(delivered.getActualDeliveryDate());
        verify(shippingEventPublisher, times(1)).publishShipmentDelivered(any(Shipment.class));
        verify(shipmentHistoryRepository, times(2)).save(any(ShipmentHistory.class));
    }

    @Test
    @DisplayName("Illegal status transition throws BadRequestException: DELIVERED -> PENDING")
    void testUpdateShipmentStatus_IllegalTransition_ThrowsBadRequestException() {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber("TRACK-999")
                .status("DELIVERED")
                .build();

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(shipment));

        assertThrows(BadRequestException.class, () ->
                shipmentService.updateShipmentStatus(orderId, "PENDING"));
    }

    @Test
    @DisplayName("Tracking number lookup returns tracking DTO with milestone history")
    void testGetTrackingDto() {
        String trackingNum = "TRACK-BLU-999999";
        UUID orderId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(shipmentId)
                .orderId(orderId)
                .trackingNumber(trackingNum)
                .carrier("BlueDart")
                .status("IN_TRANSIT")
                .shippingAddress("123 Market St, San Francisco, CA")
                .build();

        ShipmentHistory h1 = ShipmentHistory.builder()
                .id(UUID.randomUUID())
                .shipmentId(shipmentId)
                .orderId(orderId)
                .status("PENDING")
                .location("Warehouse")
                .remarks("Created")
                .build();

        when(shipmentRepository.findByTrackingNumber(trackingNum)).thenReturn(Optional.of(shipment));
        when(shipmentHistoryRepository.findByShipmentIdOrderByCreatedAtAsc(shipmentId)).thenReturn(List.of(h1));

        TrackingResponseDto dto = shipmentService.getTrackingDto(trackingNum);

        assertNotNull(dto);
        assertEquals(trackingNum, dto.getTrackingNumber());
        assertEquals("IN_TRANSIT", dto.getStatus());
        assertEquals(1, dto.getMilestones().size());
        assertEquals("PENDING", dto.getMilestones().get(0).getStatus());
    }

    @Test
    @DisplayName("Cancel shipment transitions PENDING shipment to CANCELLED")
    void testCancelShipmentIfPending() {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .status("PENDING")
                .build();

        when(shipmentRepository.findByOrderId(orderId)).thenReturn(Optional.of(shipment));

        shipmentService.cancelShipmentIfPending(orderId, "Customer requested cancellation");

        assertEquals("CANCELLED", shipment.getStatus());
        verify(shipmentRepository, times(1)).save(shipment);
        verify(shipmentHistoryRepository, times(1)).save(any(ShipmentHistory.class));
    }
}
