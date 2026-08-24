package com.bytevault.payment.service;

import com.bytevault.payment.dto.PaymentOrderRequest;
import com.bytevault.payment.dto.PaymentOrderResponse;
import com.bytevault.payment.dto.PaymentVerifyRequest;

public interface PaymentService {
    PaymentOrderResponse createOrder(PaymentOrderRequest request) throws Exception;
    void verifyPayment(PaymentVerifyRequest request) throws Exception;
}
