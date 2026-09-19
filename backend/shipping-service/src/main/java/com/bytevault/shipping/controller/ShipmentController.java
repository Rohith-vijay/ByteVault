package com.bytevault.shipping.controller;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.shipping.dto.*;
import com.bytevault.shipping.entity.Shipment;
import com.bytevault.shipping.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<Shipment>> getShipmentByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Shipment retrieved", shipmentService.getShipmentByOrderId(orderId)));
    }

    @GetMapping("/order/{orderId}/details")
    public ResponseEntity<ApiResponse<ShipmentResponseDto>> getShipmentDetailsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success("Shipment details retrieved", shipmentService.getShipmentDtoByOrderId(orderId)));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<ApiResponse<Shipment>> getShipmentByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(ApiResponse.success("Shipment retrieved", shipmentService.getShipmentByTrackingNumber(trackingNumber)));
    }

    @GetMapping("/track/{trackingNumber}/details")
    public ResponseEntity<ApiResponse<TrackingResponseDto>> getTrackingDetails(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(ApiResponse.success("Tracking details retrieved", shipmentService.getTrackingDto(trackingNumber)));
    }

    @PutMapping("/order/{orderId}/status")
    public ResponseEntity<ApiResponse<Shipment>> updateShipmentStatus(
            @PathVariable UUID orderId,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) ShipmentStatusUpdateRequest request) {
        String newStatus = request != null && request.getStatus() != null ? request.getStatus() : status;
        String location = request != null ? request.getLocation() : "Logistics Hub";
        String remarks = request != null ? request.getRemarks() : "Status updated";
        Shipment updated = shipmentService.updateShipmentStatus(orderId, newStatus, location, remarks);
        return ResponseEntity.ok(ApiResponse.success("Shipment status updated", updated));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Shipment>> createShipment(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String shippingAddress,
            @RequestParam(required = false) String carrier,
            @RequestBody(required = false) CreateShipmentRequest request) {
        Shipment shipment;
        if (request != null && request.getOrderId() != null) {
            shipment = shipmentService.createShipment(request);
        } else {
            shipment = shipmentService.createShipment(orderId, shippingAddress, carrier);
        }
        return ResponseEntity.ok(ApiResponse.success("Shipment created", shipment));
    }
}
