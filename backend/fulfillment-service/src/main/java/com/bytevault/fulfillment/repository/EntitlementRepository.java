package com.bytevault.fulfillment.repository;

import com.bytevault.fulfillment.entity.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {
    List<Entitlement> findByUserId(UUID userId);
    Optional<Entitlement> findByUserIdAndProductIdAndStatus(UUID userId, UUID productId, String status);
}
