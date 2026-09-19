package com.bytevault.shipping.repository;

import com.bytevault.shipping.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByOrderId(UUID orderId);
    Optional<Shipment> findByTrackingNumber(String trackingNumber);
    List<Shipment> findByCustomerId(UUID customerId);
    List<Shipment> findByVendorId(UUID vendorId);
}
