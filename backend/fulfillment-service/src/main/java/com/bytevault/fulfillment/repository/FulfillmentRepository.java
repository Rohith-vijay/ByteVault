package com.bytevault.fulfillment.repository;

import com.bytevault.fulfillment.entity.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {
    Optional<Fulfillment> findByOrderId(UUID orderId);
}
