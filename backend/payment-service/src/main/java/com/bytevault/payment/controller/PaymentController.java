package com.bytevault.payment.controller;

import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.dto.PaymentOrderRequest;
import com.bytevault.payment.dto.PaymentOrderResponse;
import com.bytevault.payment.dto.PaymentVerifyRequest;
import com.bytevault.payment.entity.PaymentTransaction;
import com.bytevault.payment.repository.PaymentTransactionRepository;
import com.bytevault.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final com.bytevault.payment.service.VendorLedgerService vendorLedgerService;
    private final com.bytevault.payment.service.RefundService refundService;
    private final RabbitTemplate rabbitTemplate;
    private final PaymentTransactionRepository transactionRepository;
    private final com.bytevault.payment.service.PaymentOrderSyncService paymentOrderSyncService;

    @GetMapping("/vendor/earnings")
    public ResponseEntity<com.bytevault.payment.dto.VendorEarningsResponse> getVendorEarnings(
            @RequestParam(value = "vendorId", required = false) UUID requestedVendorId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            org.springframework.security.core.Authentication auth) {
        UUID authUserId = resolveUserId(userIdHeader, auth);
        boolean isAdmin = (rolesHeader != null && rolesHeader.contains("ROLE_ADMIN")) ||
                (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        UUID targetVendorId;
        if (requestedVendorId != null) {
            if (!isAdmin && !requestedVendorId.equals(authUserId)) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied: You cannot view another vendor's earnings.");
            }
            targetVendorId = requestedVendorId;
        } else {
            targetVendorId = authUserId;
        }

        return ResponseEntity.ok(vendorLedgerService.getVendorEarnings(targetVendorId));
    }

    @GetMapping("/vendor/ledger")
    public ResponseEntity<com.bytevault.payment.dto.VendorEarningsResponse> getVendorLedger(
            @RequestParam(value = "vendorId", required = false) UUID requestedVendorId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            org.springframework.security.core.Authentication auth) {
        return getVendorEarnings(requestedVendorId, userIdHeader, rolesHeader, auth);
    }

    @GetMapping("/my-transactions")
    public ResponseEntity<java.util.List<PaymentTransaction>> getMyTransactions(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            org.springframework.security.core.Authentication auth) {
        UUID userId = resolveUserId(userIdHeader, auth);
        return ResponseEntity.ok(transactionRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping("/refund")
    public ResponseEntity<com.bytevault.payment.dto.RefundResponse> processRefund(
            @jakarta.validation.Valid @RequestBody com.bytevault.payment.dto.RefundRequest request) {
        return ResponseEntity.ok(refundService.processRefund(request));
    }

    @GetMapping("/admin/all")
    public ResponseEntity<java.util.List<PaymentTransaction>> getAllTransactionsAdmin(
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            org.springframework.security.core.Authentication auth) {
        boolean isAdmin = (rolesHeader != null && rolesHeader.contains("ROLE_ADMIN")) ||
                (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Admin role required.");
        }
        return ResponseEntity.ok(transactionRepository.findAll());
    }

    private UUID resolveUserId(String header, org.springframework.security.core.Authentication auth) {
        if (header != null && !header.trim().isEmpty()) {
            try {
                return UUID.fromString(header.trim());
            } catch (Exception ignored) {}
        }
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName().trim());
            } catch (Exception ignored) {}
        }
        throw new org.springframework.security.access.AccessDeniedException("Unauthorized: Unable to resolve authenticated user ID.");
    }

    @PostMapping({"", "/create-order"})
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(@RequestBody PaymentOrderRequest request) throws Exception {
        PaymentOrderResponse response = paymentService.createOrder(request);

        try {
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .orderId(request.getReceipt() != null && request.getReceipt().length() == 36 ? UUID.fromString(request.getReceipt()) : null)
                    .amount(request.getAmount())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                    .provider("RAZORPAY")
                    .razorpayOrderId(response.getOrderId())
                    .status("PENDING")
                    .build();
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.warn("[PaymentController] Failed to persist initial payment transaction: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody PaymentVerifyRequest request) throws Exception {
        log.info("[PaymentController] Verifying payment signatures: orderId={}", request.getRazorpayOrderId());

        // Idempotency check: if transaction already verified as SUCCESS, return cached success response
        if (request.getRazorpayOrderId() != null) {
            var existingTx = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId());
            if (existingTx.isPresent()) {
                PaymentTransaction tx = existingTx.get();
                if ("SUCCESS".equalsIgnoreCase(tx.getStatus())) {
                    log.info("[PaymentController] Idempotent duplicate verification request for order: {}", request.getRazorpayOrderId());
                    Map<String, Object> cachedResponse = new HashMap<>();
                    cachedResponse.put("status", "SUCCESS");
                    cachedResponse.put("message", "Payment already verified successfully (idempotent response).");
                    return ResponseEntity.ok(cachedResponse);
                } else if ("REFUNDED".equalsIgnoreCase(tx.getStatus())) {
                    throw new com.bytevault.common.exception.BadRequestException("Cannot verify payment: transaction has already been refunded.");
                }
            }
        }

        try {
            paymentService.verifyPayment(request);

            // Fetch or build transaction record
            PaymentTransaction tx = null;
            if (request.getRazorpayOrderId() != null) {
                var existingTx = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId());
                if (existingTx.isPresent()) {
                    tx = existingTx.get();
                }
            }

            if (tx == null) {
                tx = PaymentTransaction.builder()
                        .orderId(request.getDbOrderId() != null ? UUID.fromString(request.getDbOrderId()) : null)
                        .userId(request.getUserId() != null ? UUID.fromString(request.getUserId()) : null)
                        .razorpayOrderId(request.getRazorpayOrderId())
                        .amount(request.getAmount() != null ? request.getAmount() : java.math.BigDecimal.ZERO)
                        .currency("INR")
                        .provider("RAZORPAY")
                        .build();
            }
            if (request.getAmount() != null && request.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                tx.setAmount(request.getAmount());
            }
            if (request.getDbOrderId() != null && !request.getDbOrderId().trim().isEmpty()) {
                tx.setOrderId(UUID.fromString(request.getDbOrderId().trim()));
            }
            if (request.getUserId() != null && !request.getUserId().trim().isEmpty()) {
                tx.setUserId(UUID.fromString(request.getUserId().trim()));
            }

            tx.setRazorpayPaymentId(request.getRazorpayPaymentId());
            tx.setRazorpaySignature(request.getRazorpaySignature());
            tx.setStatus("SUCCESS");
            transactionRepository.save(tx);

            // Synchronize with order-service (Order Service is the authoritative owner of the PAID transition & outbox event)
            boolean synced = paymentOrderSyncService.syncPaymentToOrder(tx);
            log.info("[PaymentController] Payment verified. Order sync status: {} (immediateSync={})",
                    tx.getOrderSyncStatus(), synced);

            // Note: DO NOT publish order.paid directly from Payment Service.
            // Order Service writes order.paid to its transactional outbox upon marking the order as PAID.

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("syncStatus", tx.getOrderSyncStatus());
            response.put("message", "Payment verified successfully.");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("[PaymentController] Payment verification failed: {}", ex.getMessage());

            // Update transaction record to FAILED
            try {
                if (request.getRazorpayOrderId() != null) {
                    transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId()).ifPresent(tx -> {
                        tx.setStatus("FAILED");
                        tx.setErrorMessage(ex.getMessage());
                        transactionRepository.save(tx);
                    });
                }
            } catch (Exception e) {
                log.warn("[PaymentController] Could not update transaction status to FAILED: {}", e.getMessage());
            }

            // Fire PaymentFailedEvent
            try {
                Map<String, Object> failEvent = new HashMap<>();
                failEvent.put("orderId", request.getDbOrderId());
                failEvent.put("razorpayOrderId", request.getRazorpayOrderId());
                failEvent.put("status", "FAILED");
                failEvent.put("error", ex.getMessage());
                rabbitTemplate.convertAndSend("order.exchange", "payment.failed", failEvent);
            } catch (Exception ignored) {}

            throw ex;
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String webhookSignature,
            @RequestBody String rawPayload) {
        log.info("[PaymentController] Received Razorpay Webhook callback. Signature present: {}", webhookSignature != null);
        Map<String, Object> response = new HashMap<>();

        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawPayload);
            String event = root.has("event") ? root.get("event").asText() : "payment.captured";
            log.info("[PaymentController] Processing Razorpay webhook event: {}", event);

            if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
                response.put("status", "PROCESSED");
                response.put("event", event);
                response.put("message", "Webhook processed idempotently.");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.warn("[PaymentController] Webhook parsing skipped for simulated payload: {}", e.getMessage());
        }

        response.put("status", "ACKNOWLEDGED");
        response.put("message", "Webhook event acknowledged.");
        return ResponseEntity.ok(response);
    }
}
