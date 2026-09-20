package com.example.platform.payment;

import com.example.platform.payment.dto.PaymentOrderRequest;
import com.example.platform.payment.dto.PaymentOrderResponse;
import com.example.platform.payment.dto.PaymentVerifyRequest;

public interface PaymentService {
    PaymentOrderResponse createOrder(PaymentOrderRequest request) throws Exception;
    void verifyPayment(PaymentVerifyRequest request) throws Exception;
}
