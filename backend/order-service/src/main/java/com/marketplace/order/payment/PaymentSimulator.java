package com.marketplace.order.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class PaymentSimulator {
}
