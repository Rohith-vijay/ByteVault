package com.bytevault.payment.controller;

import com.bytevault.payment.dto.PaymentOrderRequest;
import com.bytevault.payment.dto.PaymentOrderResponse;
import com.bytevault.payment.dto.PaymentVerifyRequest;
import com.bytevault.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping
    public ResponseEntity<PaymentOrderResponse> createPaymentOrder(@RequestBody PaymentOrderRequest request) throws Exception {
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody PaymentVerifyRequest request) throws Exception {
        log.info("[PaymentController] Verifying payment signatures: orderId={}", request.getRazorpayOrderId());
        paymentService.verifyPayment(request);

        // Success -> Fire OrderPaidEvent
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", request.getDbOrderId());
        eventData.put("razorpayOrderId", request.getRazorpayOrderId());
        eventData.put("razorpayPaymentId", request.getRazorpayPaymentId());
        eventData.put("status", "PAID");

        log.info("[PaymentController] Publishing OrderPaidEvent to RabbitMQ: {}", eventData);
        rabbitTemplate.convertAndSend("order.exchange", "order.paid", eventData);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Payment verified successfully.");
        return ResponseEntity.ok(response);
    }
}
