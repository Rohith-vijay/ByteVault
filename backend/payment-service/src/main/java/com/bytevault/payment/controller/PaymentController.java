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
    private final RabbitTemplate rabbitTemplate;
    private final PaymentTransactionRepository transactionRepository;
    private final OrderClient orderClient;

    @PostMapping
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

        try {
            paymentService.verifyPayment(request);

            // Update transaction record to SUCCESS
            try {
                if (request.getRazorpayOrderId() != null) {
                    transactionRepository.findByRazorpayOrderId(request.getRazorpayOrderId()).ifPresent(tx -> {
                        tx.setRazorpayPaymentId(request.getRazorpayPaymentId());
                        tx.setRazorpaySignature(request.getRazorpaySignature());
                        tx.setStatus("SUCCESS");
                        transactionRepository.save(tx);
                    });
                }
            } catch (Exception e) {
                log.warn("[PaymentController] Could not update transaction status: {}", e.getMessage());
            }

            // Sync with order-service via Feign
            try {
                if (request.getDbOrderId() != null) {
                    orderClient.updateOrderStatus(UUID.fromString(request.getDbOrderId()), "PAID");
                    log.info("[PaymentController] Synchronized PAID status with order-service: orderId={}", request.getDbOrderId());
                }
            } catch (Exception e) {
                log.warn("[PaymentController] Could not notify order-service via Feign: {}", e.getMessage());
            }

            // Success -> Fire OrderPaidEvent
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("orderId", request.getDbOrderId());
            eventData.put("razorpayOrderId", request.getRazorpayOrderId());
            eventData.put("razorpayPaymentId", request.getRazorpayPaymentId());
            eventData.put("userId", request.getUserId());
            eventData.put("productIds", request.getProductIds());
            eventData.put("customerEmail", request.getCustomerEmail());
            eventData.put("customerName", request.getCustomerName());
            eventData.put("status", "PAID");

            log.info("[PaymentController] Publishing OrderPaidEvent to RabbitMQ: {}", eventData);
            rabbitTemplate.convertAndSend("order.exchange", "order.paid", eventData);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Payment verified successfully.");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("[PaymentController] Payment verification failed: {}", ex.getMessage());

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
}
