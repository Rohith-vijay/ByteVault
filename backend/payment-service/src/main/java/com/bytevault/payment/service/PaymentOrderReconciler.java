package com.bytevault.payment.service;

import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderReconciler {

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentOrderSyncService paymentOrderSyncService;

    @Scheduled(fixedDelayString = "${app.reconciler.interval-ms:5000}")
    public void reconcilePendingSyncs() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentTransaction> pending = transactionRepository.findPendingOrderSyncs(now);
        if (pending.isEmpty()) {
            return;
        }

        log.info("[PaymentOrderReconciler] Found {} pending payment-to-order handoffs to reconcile", pending.size());

        for (PaymentTransaction tx : pending) {
            try {
                // Multi-worker safe lease acquisition:
                // Atomically advance nextSyncRetryAt by 30 seconds to lock the record
                LocalDateTime leaseUntil = now.plusSeconds(30);
                boolean leaseAcquired = tryAcquireLease(tx.getId(), now, leaseUntil);
                if (!leaseAcquired) {
                    log.debug("[PaymentOrderReconciler] Lease skipped for txId={}, already claimed by concurrent instance", tx.getId());
                    continue;
                }

                // Re-fetch claimed transaction
                transactionRepository.findById(tx.getId()).ifPresent(paymentOrderSyncService::syncPaymentToOrder);

            } catch (Exception e) {
                log.error("[PaymentOrderReconciler] Error during reconciliation for txId={}: {}", tx.getId(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquireLease(UUID txId, LocalDateTime now, LocalDateTime leaseUntil) {
        return transactionRepository.acquireReconciliationLease(txId, now, leaseUntil) > 0;
    }
}
