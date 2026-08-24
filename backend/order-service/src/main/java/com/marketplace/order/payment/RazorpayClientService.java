package com.marketplace.order.payment;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RazorpayClientService {

    private final RazorpayClient razorpayClient;

    public Order createOrder(BigDecimal amount, String currency, String receipt) throws Exception {
        JSONObject options = new JSONObject();
        options.put("amount", amount.multiply(BigDecimal.valueOf(100))); // convert to paise
        options.put("currency", currency != null ? currency : "INR");
        options.put("receipt", receipt != null ? receipt : "txn_" + System.currentTimeMillis());

        return razorpayClient.orders.create(options);
    }

    public RazorpayClient getRazorpayClient() {
        return razorpayClient;
    }
}
