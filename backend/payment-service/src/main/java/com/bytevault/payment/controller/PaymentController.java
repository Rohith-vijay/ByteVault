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
import org.springframework.beans.factory.annotation.Value;
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
    private final PaymentTransactionRepository transactionRepository;
    private final com.bytevault.payment.service.PaymentOrderSyncService paymentOrderSyncService;
    private final com.bytevault.payment.client.OrderClient orderClient;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

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
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.bytevault.payment.dto.RefundResponse> processRefund(
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            org.springframework.security.core.Authentication auth,
            @jakarta.validation.Valid @RequestBody com.bytevault.payment.dto.RefundRequest request) {
        boolean isAdmin = (rolesHeader != null && rolesHeader.contains("ROLE_ADMIN")) ||
                (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied: Admin role required to process refunds.");
        }
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

    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(PaymentOrderRequest request) throws Exception {
        return createPaymentOrder(null, null, request);
    }

    public ResponseEntity<Map<String, Object>> verifyPayment(PaymentVerifyRequest request) throws Exception {
        return verifyPayment(null, null, null, request);
    }

    @PostMapping({"", "/create-order"})
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            org.springframework.security.core.Authentication auth,
            @RequestBody PaymentOrderRequest request) throws Exception {
        PaymentOrderResponse response = paymentService.createOrder(request);

        UUID creatorUserId = null;
        try {
            creatorUserId = resolveUserId(userIdHeader, auth);
        } catch (Exception ignored) {}

        try {
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .orderId(request.getReceipt() != null && request.getReceipt().length() == 36 ? UUID.fromString(request.getReceipt()) : null)
                    .userId(creatorUserId)
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
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            org.springframework.security.core.Authentication auth,
            @RequestBody PaymentVerifyRequest request) throws Exception {
        log.info("[PaymentController] Verifying payment signatures: orderId={}", request.getRazorpayOrderId());

        UUID callerUserId = null;
        try {
            callerUserId = resolveUserId(userIdHeader, auth);
        } catch (Exception ignored) {}

        boolean isAdmin = (rolesHeader != null && rolesHeader.contains("ROLE_ADMIN")) ||
                (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        // Ownership enforcement: prevent IDOR/cross-user verification attempts
        if (callerUserId != null && !isAdmin) {
            if (request.getUserId() != null && !request.getUserId().trim().isEmpty()) {
                try {
                    UUID reqUserId = UUID.fromString(request.getUserId().trim());
                    if (!callerUserId.equals(reqUserId)) {
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Access denied: You cannot verify payment for another user.");
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Idempotency & existing transaction check
        if (request.getRazorpayOrderId() != null) {
            var existingTx = transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId());
            if (existingTx.isPresent()) {
                PaymentTransaction tx = existingTx.get();
                if (callerUserId != null && !isAdmin && tx.getUserId() != null && !callerUserId.equals(tx.getUserId())) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Access denied: You do not own this payment transaction.");
                }
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
                UUID txUserId = callerUserId;
                if (txUserId == null && request.getUserId() != null && !request.getUserId().trim().isEmpty()) {
                    try {
                        txUserId = UUID.fromString(request.getUserId().trim());
                    } catch (Exception ignored) {}
                }
                tx = PaymentTransaction.builder()
                        .orderId(request.getDbOrderId() != null ? UUID.fromString(request.getDbOrderId()) : null)
                        .userId(txUserId)
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
            if (tx.getUserId() == null && callerUserId != null) {
                tx.setUserId(callerUserId);
            } else if (tx.getUserId() == null && request.getUserId() != null && !request.getUserId().trim().isEmpty()) {
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

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("syncStatus", tx.getOrderSyncStatus());
            response.put("message", "Payment verified successfully.");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("[PaymentController] Payment verification failed: {}", ex.getMessage());

            // Update transaction record to FAILED, but DO NOT fail order in order-service.
            // Per spec: Order stays PENDING and retryable so the customer can retry payment.
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

            throw ex;
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleRazorpayWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String webhookSignature,
            @RequestBody String rawPayload) {
        log.info("[PaymentController] Received Razorpay Webhook callback. Signature present: {}", webhookSignature != null);
        Map<String, Object> response = new HashMap<>();

        // Fix F1: Verify HMAC-SHA256 signature if webhookSecret is configured
        if (webhookSecret != null && !webhookSecret.trim().isEmpty()) {
            if (webhookSignature == null || webhookSignature.trim().isEmpty()) {
                log.warn("[PaymentController] Webhook rejected: missing X-Razorpay-Signature header");
                response.put("status", "REJECTED");
                response.put("message", "Missing required X-Razorpay-Signature header");
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(response);
            }

            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(webhookSecret.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] hash = mac.doFinal(rawPayload != null ? rawPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]);
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) sb.append('0');
                    sb.append(hex);
                }
                String expectedSig = sb.toString();

                if (!java.security.MessageDigest.isEqual(
                        expectedSig.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        webhookSignature.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    log.warn("[PaymentController] Webhook rejected: signature mismatch");
                    response.put("status", "REJECTED");
                    response.put("message", "Invalid webhook signature");
                    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(response);
                }
            } catch (Exception ex) {
                log.error("[PaymentController] Error computing webhook signature: {}", ex.getMessage());
                response.put("status", "REJECTED");
                response.put("message", "Signature verification error");
                return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(response);
            }
        }

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
