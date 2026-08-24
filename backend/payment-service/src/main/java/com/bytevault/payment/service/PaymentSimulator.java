package com.bytevault.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!prod")
public class PaymentSimulator {
    public PaymentSimulator() {
        log.info("[PaymentSimulator] Initialized sandbox simulator. Real Razorpay API client bypassed.");
    }
}
