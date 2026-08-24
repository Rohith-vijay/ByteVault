package com.bytevault.fulfillment.event;

import com.bytevault.fulfillment.service.FulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

    private final FulfillmentService fulfillmentService;

    @RabbitListener(queues = "order.queue.paid")
    public void handleOrderPaid(Map<String, Object> event) {
        log.info("[OrderPaidEventListener] OrderPaidEvent consumed: {}", event);

        try {
            String orderIdStr = (String) event.get("orderId");
            String userIdStr = (String) event.get("userId");
            
            // Defaulting mock products if not present
            List<String> productIdsStr = (List<String>) event.get("productIds");
            if (productIdsStr == null) {
                productIdsStr = new ArrayList<>();
            }

            UUID orderId = UUID.fromString(orderIdStr);
            UUID userId = UUID.fromString(userIdStr != null ? userIdStr : UUID.randomUUID().toString());

            List<UUID> productIds = new ArrayList<>();
            for (String pid : productIdsStr) {
                productIds.add(UUID.fromString(pid));
            }

            // Grant download entitlements
            fulfillmentService.fulfillDigitalOrder(orderId, userId, productIds);
            log.info("[OrderPaidEventListener] Digital fulfillment completed for order: {}", orderId);
        } catch (Exception e) {
            log.error("[OrderPaidEventListener] Failed to process digital order fulfillment event", e);
            // In a real environment, dead-letter routing or exceptions will trigger redeliveries.
        }
    }
}
