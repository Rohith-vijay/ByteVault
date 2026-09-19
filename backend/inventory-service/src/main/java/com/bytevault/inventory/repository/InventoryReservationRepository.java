package com.bytevault.inventory.repository;

import com.bytevault.inventory.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    List<InventoryReservation> findByOrderId(UUID orderId);
    List<InventoryReservation> findByOrderIdAndStatus(UUID orderId, String status);
    java.util.Optional<InventoryReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);
}
