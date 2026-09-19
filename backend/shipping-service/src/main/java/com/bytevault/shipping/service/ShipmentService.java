package com.bytevault.shipping.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.shipping.dto.*;
import com.bytevault.shipping.entity.Shipment;
import com.bytevault.shipping.entity.ShipmentHistory;
import com.bytevault.shipping.event.ShippingEventPublisher;
import com.bytevault.shipping.repository.ShipmentHistoryRepository;
import com.bytevault.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentHistoryRepository shipmentHistoryRepository;
    private final ShippingEventPublisher shippingEventPublisher;

    @Transactional
    public Shipment createShipment(UUID orderId, String shippingAddress, String carrier) {
        return createOrGetShipment(orderId, null, null, shippingAddress, carrier, null);
    }

    @Transactional
    public Shipment createShipment(CreateShipmentRequest request) {
        return createOrGetShipment(
                request.getOrderId(),
                request.getCustomerId(),
                request.getVendorId(),
                request.getShippingAddress(),
                request.getCarrier(),
                request.getWeightKg()
        );
    }

    @Transactional
    public Shipment createOrGetShipment(UUID orderId, UUID customerId, UUID vendorId, String shippingAddress, String carrier, Double weightKg) {
        Optional<Shipment> existing = shipmentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("[ShipmentService] Idempotent replay: Shipment already exists for orderId={}", orderId);
            return existing.get();
        }

        String trackingNumber = generateTrackingNumber(carrier);
        Shipment shipment = Shipment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .vendorId(vendorId)
                .trackingNumber(trackingNumber)
                .carrier(carrier != null && !carrier.isBlank() ? carrier : "Standard Post")
                .status("PENDING")
                .shippingAddress(shippingAddress)
                .estimatedDeliveryDate(LocalDateTime.now().plusDays(4))
                .weightKg(weightKg != null ? weightKg : 1.0)
                .build();

        Shipment saved = shipmentRepository.save(shipment);

        // Record initial milestone
        recordMilestone(saved.getId(), orderId, "PENDING", "Origin Facility", "Shipment order registered in system");

        // Publish shipment created event
        shippingEventPublisher.publishShipmentCreated(saved);

        log.info("[ShipmentService] Created shipment for orderId={}, trackingNumber={}", orderId, trackingNumber);
        return saved;
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public Shipment getShipmentByTrackingNumber(String trackingNumber) {
        return shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for tracking number: " + trackingNumber));
    }

    @Transactional(readOnly = true)
    public ShipmentResponseDto getShipmentDtoByOrderId(UUID orderId) {
        Shipment shipment = getShipmentByOrderId(orderId);
        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentIdOrderByCreatedAtAsc(shipment.getId());
        return mapToDto(shipment, history);
    }

    @Transactional(readOnly = true)
    public TrackingResponseDto getTrackingDto(String trackingNumber) {
        Shipment shipment = getShipmentByTrackingNumber(trackingNumber);
        List<ShipmentHistory> history = shipmentHistoryRepository.findByShipmentIdOrderByCreatedAtAsc(shipment.getId());
        return TrackingResponseDto.builder()
                .trackingNumber(shipment.getTrackingNumber())
                .orderId(shipment.getOrderId())
                .carrier(shipment.getCarrier())
                .status(shipment.getStatus())
                .destinationSummary(extractDestinationCity(shipment.getShippingAddress()))
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .actualDeliveryDate(shipment.getActualDeliveryDate())
                .milestones(history.stream().map(this::mapHistoryToDto).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public Shipment updateShipmentStatus(UUID orderId, String newStatus) {
        return updateShipmentStatus(orderId, newStatus, "Logistics Hub", "Status updated to " + newStatus);
    }

    @Transactional
    public Shipment updateShipmentStatus(UUID orderId, String newStatus, String location, String remarks) {
        Shipment shipment = getShipmentByOrderId(orderId);
        String currentStatus = shipment.getStatus();

        if (currentStatus.equalsIgnoreCase(newStatus)) {
            log.info("[ShipmentService] Status already set to {} for orderId={}", newStatus, orderId);
            return shipment;
        }

        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new BadRequestException("Invalid shipment status transition from " + currentStatus + " to " + newStatus);
        }

        shipment.setStatus(newStatus.toUpperCase());
        if ("DELIVERED".equalsIgnoreCase(newStatus)) {
            shipment.setActualDeliveryDate(LocalDateTime.now());
        }

        Shipment updated = shipmentRepository.save(shipment);

        // Record history milestone
        recordMilestone(shipment.getId(), orderId, newStatus.toUpperCase(), location, remarks);

        // Publish events
        if ("IN_TRANSIT".equalsIgnoreCase(newStatus) || "SHIPPED".equalsIgnoreCase(newStatus) || "PICKED_UP".equalsIgnoreCase(newStatus)) {
            shippingEventPublisher.publishShipmentDispatched(updated);
        } else if ("DELIVERED".equalsIgnoreCase(newStatus)) {
            shippingEventPublisher.publishShipmentDelivered(updated);
        }

        log.info("[ShipmentService] Updated shipment status for orderId={}, newStatus={}", orderId, newStatus);
        return updated;
    }

    @Transactional
    public void cancelShipmentIfPending(UUID orderId, String reason) {
        Optional<Shipment> opt = shipmentRepository.findByOrderId(orderId);
        if (opt.isEmpty()) return;

        Shipment shipment = opt.get();
        if ("PENDING".equalsIgnoreCase(shipment.getStatus()) || "MANIFESTED".equalsIgnoreCase(shipment.getStatus())) {
            shipment.setStatus("CANCELLED");
            shipmentRepository.save(shipment);
            recordMilestone(shipment.getId(), orderId, "CANCELLED", "Fulfillment Center", reason);
            log.info("[ShipmentService] Cancelled pending shipment for orderId={}", orderId);
        } else {
            log.warn("[ShipmentService] Cannot cancel shipment for orderId={} in active state: {}", orderId, shipment.getStatus());
        }
    }

    private void recordMilestone(UUID shipmentId, UUID orderId, String status, String location, String remarks) {
        ShipmentHistory history = ShipmentHistory.builder()
                .shipmentId(shipmentId)
                .orderId(orderId)
                .status(status)
                .location(location != null ? location : "Hub")
                .remarks(remarks != null ? remarks : "")
                .build();
        shipmentHistoryRepository.save(history);
    }

    private boolean isValidStatusTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equalsIgnoreCase(to)) return true;

        String f = from.toUpperCase();
        String t = to.toUpperCase();

        return switch (f) {
            case "PENDING" -> t.equals("MANIFESTED") || t.equals("PICKED_UP") || t.equals("SHIPPED") || t.equals("CANCELLED");
            case "MANIFESTED" -> t.equals("PICKED_UP") || t.equals("SHIPPED") || t.equals("IN_TRANSIT") || t.equals("CANCELLED");
            case "PICKED_UP", "SHIPPED" -> t.equals("IN_TRANSIT") || t.equals("OUT_FOR_DELIVERY") || t.equals("DELIVERED") || t.equals("RETURNED") || t.equals("CANCELLED");
            case "IN_TRANSIT" -> t.equals("OUT_FOR_DELIVERY") || t.equals("DELIVERED") || t.equals("RETURNED");
            case "OUT_FOR_DELIVERY" -> t.equals("DELIVERED") || t.equals("RETURNED");
            case "DELIVERED", "RETURNED", "CANCELLED" -> false;
            default -> false;
        };
    }

    private String generateTrackingNumber(String carrier) {
        String prefix = "BV";
        if (carrier != null) {
            String c = carrier.toUpperCase();
            if (c.contains("FEDEX")) prefix = "FDX";
            else if (c.contains("DHL")) prefix = "DHL";
            else if (c.contains("UPS")) prefix = "UPS";
            else if (c.contains("BLUEDART")) prefix = "BLU";
        }
        return "TRACK-" + prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String extractDestinationCity(String address) {
        if (address == null || address.isBlank()) return "Destination Address";
        String[] parts = address.split(",");
        if (parts.length >= 2) {
            return parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
        }
        return address;
    }

    private ShipmentResponseDto mapToDto(Shipment shipment, List<ShipmentHistory> history) {
        return ShipmentResponseDto.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrderId())
                .customerId(shipment.getCustomerId())
                .vendorId(shipment.getVendorId())
                .trackingNumber(shipment.getTrackingNumber())
                .carrier(shipment.getCarrier())
                .status(shipment.getStatus())
                .shippingAddress(shipment.getShippingAddress())
                .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
                .actualDeliveryDate(shipment.getActualDeliveryDate())
                .weightKg(shipment.getWeightKg())
                .notes(shipment.getNotes())
                .milestones(history.stream().map(this::mapHistoryToDto).collect(Collectors.toList()))
                .build();
    }

    private ShipmentHistoryDto mapHistoryToDto(ShipmentHistory history) {
        return ShipmentHistoryDto.builder()
                .id(history.getId())
                .status(history.getStatus())
                .location(history.getLocation())
                .remarks(history.getRemarks())
                .timestamp(history.getCreatedAt())
                .build();
    }
}
