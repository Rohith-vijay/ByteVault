package com.marketplace.order.payment;

import com.marketplace.order.payment.dto.PaymentOrderRequest;
import com.marketplace.order.payment.dto.PaymentOrderResponse;
import com.marketplace.order.payment.dto.PaymentVerifyRequest;

public interface PaymentService {
    PaymentOrderResponse createOrder(PaymentOrderRequest request) throws Exception;
    void verifyPayment(PaymentVerifyRequest request) throws Exception;
}
