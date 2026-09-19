package com.bytevault.shipping.repository;

import com.bytevault.shipping.entity.ShipmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentHistoryRepository extends JpaRepository<ShipmentHistory, UUID> {
    List<ShipmentHistory> findByShipmentIdOrderByCreatedAtAsc(UUID shipmentId);
    List<ShipmentHistory> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
