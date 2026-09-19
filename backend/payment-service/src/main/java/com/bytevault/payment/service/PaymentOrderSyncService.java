package com.bytevault.payment.service;

import com.bytevault.common.dto.OrderPaymentConfirmationRequest;
import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderSyncService {

    public static final int MAX_SYNC_ATTEMPTS = 5;

    private final OrderClient orderClient;
    private final PaymentTransactionRepository transactionRepository;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    /**
     * Synchronize a verified payment transaction with Order Service.
     * Order Service is the authoritative owner of the PAID transition and transactional outbox.
     *
     * @param transaction The payment transaction to sync
     * @return true if successfully synced or idempotently confirmed, false otherwise
     */
    @Transactional
    public boolean syncPaymentToOrder(PaymentTransaction transaction) {
        if (transaction.getOrderId() == null) {
            log.warn("[PaymentOrderSync] Transaction {} has no dbOrderId, skipping sync", transaction.getId());
            transaction.setOrderSyncStatus("FAILED");
            transaction.setLastSyncError("Permanent error: No dbOrderId associated with transaction");
            transaction.setNextSyncRetryAt(null);
            transactionRepository.save(transaction);
            return false;
        }

        if ("SYNCED".equalsIgnoreCase(transaction.getOrderSyncStatus())) {
            log.info("[PaymentOrderSync] Transaction {} already marked SYNCED", transaction.getId());
            return true;
        }

        OrderPaymentConfirmationRequest request = OrderPaymentConfirmationRequest.builder()
                .paymentId(transaction.getRazorpayPaymentId() != null
                        ? transaction.getRazorpayPaymentId()
                        : transaction.getId().toString())
                .razorpayOrderId(transaction.getRazorpayOrderId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency() != null ? transaction.getCurrency() : "INR")
                .provider(transaction.getProvider() != null ? transaction.getProvider() : "RAZORPAY")
                .paymentTimestamp(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt() : LocalDateTime.now())
                .build();

        try {
            log.info("[PaymentOrderSync] Sending mark-paid to order-service for orderId={}, txId={}",
                    transaction.getOrderId(), transaction.getId());

            orderClient.markOrderPaid(
                    transaction.getOrderId(),
                    gatewaySecret,
                    "ROLE_INTERNAL_SERVICE",
                    request
            );

            transaction.setOrderSyncStatus("SYNCED");
            transaction.setLastSyncError(null);
            transaction.setNextSyncRetryAt(null);
            transactionRepository.save(transaction);

            log.info("[PaymentOrderSync] Successfully synchronized orderId={} with order-service as PAID",
                    transaction.getOrderId());
            return true;

        } catch (FeignException.BadRequest | FeignException.NotFound | FeignException.Forbidden | FeignException.Unauthorized ex) {
            String content = ex.contentUTF8();
            // Check if the 4xx actually represents an idempotent already-paid state
            if (content != null && (content.contains("already marked as PAID") || content.contains("already PAID"))) {
                log.info("[PaymentOrderSync] Order {} already verified as PAID downstream. Treating as idempotent success.",
                        transaction.getOrderId());
                transaction.setOrderSyncStatus("SYNCED");
                transaction.setLastSyncError(null);
                transaction.setNextSyncRetryAt(null);
                transactionRepository.save(transaction);
                return true;
            }

            // Permanent client error: invalid order, invalid credentials, or non-recoverable state
            String sanitizedError = sanitizeError(ex.status(), content != null && !content.isBlank() ? content : ex.getMessage());
            log.error("[PaymentOrderSync] CRITICAL: Permanent failure syncing orderId={}: HTTP {} - {}",
                    transaction.getOrderId(), ex.status(), sanitizedError);

            transaction.setOrderSyncStatus("FAILED");
            transaction.setLastSyncError("Permanent failure: " + sanitizedError);
            transaction.setNextSyncRetryAt(null);
            transactionRepository.save(transaction);
            return false;

        } catch (Exception ex) {
            // Transient failure (network timeout, 5xx, order-service temporarily down)
            int attempts = transaction.getSyncAttempts() + 1;
            transaction.setSyncAttempts(attempts);

            if (attempts >= MAX_SYNC_ATTEMPTS) {
                log.error("[PaymentOrderSync] CRITICAL: Max sync attempts ({}) exceeded for orderId={}: {}",
                        MAX_SYNC_ATTEMPTS, transaction.getOrderId(), ex.getMessage());
                transaction.setOrderSyncStatus("FAILED");
                transaction.setLastSyncError("Max sync attempts (" + MAX_SYNC_ATTEMPTS + ") reached: " + ex.getMessage());
                transaction.setNextSyncRetryAt(null);
            } else {
                long backoffSeconds = (long) Math.min(300, Math.pow(2, attempts) * 5); // 10s, 20s, 40s, 80s, 160s...
                LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
                transaction.setNextSyncRetryAt(nextRetry);
                transaction.setLastSyncError("Transient error (attempt " + attempts + "/" + MAX_SYNC_ATTEMPTS + "): " + ex.getMessage());
                log.warn("[PaymentOrderSync] Transient failure syncing orderId={}, retry in {}s. Error: {}",
                        transaction.getOrderId(), backoffSeconds, ex.getMessage());
            }

            transactionRepository.save(transaction);
            return false;
        }
    }

    private String sanitizeError(int status, String message) {
        if (message == null) return "HTTP " + status;
        // Truncate and strip any secret patterns
        String sanitized = message.replaceAll("(?i)secret=[^&\\s]+", "secret=[REDACTED]");
        if (sanitized.length() > 250) {
            sanitized = sanitized.substring(0, 250) + "...";
        }
        return "HTTP " + status + " - " + sanitized;
    }
}
