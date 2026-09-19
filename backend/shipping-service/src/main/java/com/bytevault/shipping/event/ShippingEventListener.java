package com.bytevault.shipping.event;

import com.bytevault.shipping.config.RabbitMQConfig;
import com.bytevault.shipping.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingEventListener {

    private final ShipmentService shipmentService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_PAID_QUEUE)
    public void handleOrderPaid(Map<String, Object> event) {
        log.info("[ShippingEventListener] Consumed order.paid event: {}", event);
        if (event == null || !event.containsKey("orderId")) {
            return;
        }

        try {
            UUID orderId = UUID.fromString(event.get("orderId").toString());
            String orderType = event.containsKey("orderType") && event.get("orderType") != null 
                    ? event.get("orderType").toString() 
                    : "PHYSICAL";

            if ("DIGITAL".equalsIgnoreCase(orderType)) {
                log.info("[ShippingEventListener] Skipping shipment creation for purely digital orderId={}", orderId);
                return;
            }

            String shippingAddress = event.containsKey("shippingAddress") && event.get("shippingAddress") != null
                    ? event.get("shippingAddress").toString()
                    : "Standard Shipping Address";

            UUID customerId = null;
            if (event.containsKey("userId") && event.get("userId") != null) {
                try {
                    customerId = UUID.fromString(event.get("userId").toString());
                } catch (Exception ignored) {}
            }

            shipmentService.createOrGetShipment(orderId, customerId, null, shippingAddress, "Standard Carrier", null);
            log.info("[ShippingEventListener] Prepared shipment for physical orderId={}", orderId);
        } catch (Exception e) {
            log.error("[ShippingEventListener] Error processing order.paid event: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void handleOrderCancelled(Map<String, Object> event) {
        log.info("[ShippingEventListener] Consumed order.cancelled event: {}", event);
        if (event == null || !event.containsKey("orderId")) {
            return;
        }

        try {
            UUID orderId = UUID.fromString(event.get("orderId").toString());
            shipmentService.cancelShipmentIfPending(orderId, "Order cancelled by customer/system");
        } catch (Exception e) {
            log.warn("[ShippingEventListener] Handled order.cancelled gracefully: {}", e.getMessage());
        }
    }
}
