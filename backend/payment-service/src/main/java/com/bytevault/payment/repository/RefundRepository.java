package com.bytevault.payment.repository;

import com.bytevault.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
}
