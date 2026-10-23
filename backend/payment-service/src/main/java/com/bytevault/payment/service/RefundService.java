package com.bytevault.payment.service;

import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.dto.RefundRequest;
import com.bytevault.payment.dto.RefundResponse;
import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.entity.Refund;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import com.bytevault.payment.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderClient orderClient;
    private final VendorLedgerService vendorLedgerService;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Transactional
    public RefundResponse processRefund(RefundRequest request) {
        if (request.getOrderId() == null) {
            throw new com.bytevault.common.exception.BadRequestException("Order ID is required for refund processing.");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new com.bytevault.common.exception.BadRequestException("Refund amount must be greater than zero.");
        }

        log.info("[RefundService] Processing refund request for order: {}, amount: {}", request.getOrderId(), request.getAmount());

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().trim().isEmpty()) {
            Optional<Refund> existing = refundRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                log.info("[RefundService] Idempotent replay detected for refund key: {}", request.getIdempotencyKey());
                return mapToResponse(existing.get());
            }
        }

        // Validate against original payment transaction if present
        Optional<PaymentTransaction> optTx = transactionRepository.findByOrderId(request.getOrderId());
        boolean isFullRefund = true;
        if (optTx.isPresent()) {
            PaymentTransaction tx = optTx.get();
            if ("REFUNDED".equalsIgnoreCase(tx.getStatus())) {
                throw new com.bytevault.common.exception.BadRequestException("Transaction has already been fully refunded.");
            }

            java.util.List<Refund> existingRefunds = refundRepository.findByOrderId(request.getOrderId());
            BigDecimal alreadyRefunded = existingRefunds.stream()
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .map(Refund::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal cumulativeRefund = alreadyRefunded.add(request.getAmount());
            if (cumulativeRefund.compareTo(tx.getAmount()) > 0) {
                throw new com.bytevault.common.exception.BadRequestException(
                        "Cumulative refund amount (" + cumulativeRefund + ") cannot exceed original transaction amount (" + tx.getAmount() + ").");
            }

            isFullRefund = cumulativeRefund.compareTo(tx.getAmount()) == 0;
            tx.setStatus(isFullRefund ? "REFUNDED" : "PARTIALLY_REFUNDED");
            if (!isFullRefund) {
                transactionRepository.save(tx);
            }
        }

        Refund refund = Refund.builder()
                .orderId(request.getOrderId())
                .paymentId(request.getPaymentId())
                .amount(request.getAmount())
                .reason(request.getReason() != null ? request.getReason() : "Customer / Admin requested refund")
                .status("SUCCESS")
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Refund saved = refundRepository.save(refund);

        // Adjust vendor ledger proportionally or in full
        try {
            vendorLedgerService.reverseTransactionForRefund(request.getOrderId(), request.getAmount(), isFullRefund);
            log.info("[RefundService] Adjusted vendor ledger for refunded orderId={}, isFullRefund={}", request.getOrderId(), isFullRefund);
        } catch (Exception e) {
            log.error("[RefundService] Failed to reverse vendor ledger for orderId={}: {}", request.getOrderId(), e.getMessage());
        }

        // Notify order service if fully refunded (triggers order CANCELLED and entitlement revocation)
        if (isFullRefund) {
            try {
                orderClient.refundCancel(request.getOrderId(), gatewaySecret, "ROLE_INTERNAL_SERVICE");
                log.info("[RefundService] Notified order-service of refund-cancel for fully refunded order: {}", request.getOrderId());
                if (optTx.isPresent()) {
                    PaymentTransaction tx = optTx.get();
                    tx.setOrderSyncStatus("REFUND_SYNCED");
                    transactionRepository.save(tx);
                }
            } catch (Exception e) {
                log.error("[RefundService] CRITICAL: Could not trigger refund-cancel on order-service via Feign for orderId={}: {}. Scheduled reconciler will retry.",
                        request.getOrderId(), e.getMessage());
                if (optTx.isPresent()) {
                    PaymentTransaction tx = optTx.get();
                    tx.setOrderSyncStatus("REFUND_PENDING");
                    transactionRepository.save(tx);
                }
            }
        }

        return mapToResponse(saved);
    }

    private RefundResponse mapToResponse(Refund r) {
        return RefundResponse.builder()
                .refundId(r.getId())
                .orderId(r.getOrderId())
                .paymentId(r.getPaymentId())
                .amount(r.getAmount())
                .reason(r.getReason())
                .status(r.getStatus())
                .idempotencyKey(r.getIdempotencyKey())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
