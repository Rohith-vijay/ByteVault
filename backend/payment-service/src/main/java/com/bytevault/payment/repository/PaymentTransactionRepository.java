package com.bytevault.payment.repository;

import com.bytevault.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentTransaction> findByOrderId(UUID orderId);
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'SUCCESS' " +
           "AND pt.orderSyncStatus = 'PENDING' " +
           "AND pt.orderId IS NOT NULL " +
           "AND (pt.nextSyncRetryAt IS NULL OR pt.nextSyncRetryAt <= :now) " +
           "ORDER BY pt.createdAt ASC")
    List<PaymentTransaction> findPendingOrderSyncs(@Param("now") LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE PaymentTransaction pt SET pt.nextSyncRetryAt = :leaseUntil WHERE pt.id = :id AND (pt.nextSyncRetryAt IS NULL OR pt.nextSyncRetryAt <= :now)")
    int acquireReconciliationLease(@Param("id") UUID id, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
}
