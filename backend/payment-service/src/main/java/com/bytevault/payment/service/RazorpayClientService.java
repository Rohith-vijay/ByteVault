package com.bytevault.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
public class RazorpayClientService {

    @Autowired(required = false)
    private RazorpayClient razorpayClient;

    public Order createOrder(BigDecimal amount, String currency, String receipt) throws Exception {
        if (razorpayClient == null) {
            throw new IllegalStateException("RazorpayClient is not configured.");
        }
        
        JSONObject orderRequest = new JSONObject();
        // Convert to paise (e.g. 100.00 INR = 10000 paise)
        orderRequest.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());
        orderRequest.put("currency", currency != null ? currency : "INR");
        orderRequest.put("receipt", receipt);

        log.info("[RazorpayClientService] Creating real order with request: {}", orderRequest);
        return razorpayClient.orders.create(orderRequest);
    }
}
