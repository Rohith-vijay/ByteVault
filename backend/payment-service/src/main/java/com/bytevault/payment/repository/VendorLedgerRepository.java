package com.bytevault.payment.repository;

import com.bytevault.payment.entity.VendorLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VendorLedgerRepository extends JpaRepository<VendorLedger, UUID> {
    List<VendorLedger> findByVendorIdOrderByCreatedAtDesc(UUID vendorId);
    List<VendorLedger> findByOrderId(UUID orderId);
    java.util.Optional<VendorLedger> findByOrderIdAndVendorId(UUID orderId, UUID vendorId);
    boolean existsByOrderIdAndVendorId(UUID orderId, UUID vendorId);
}
