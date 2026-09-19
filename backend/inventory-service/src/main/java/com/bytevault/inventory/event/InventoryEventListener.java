package com.bytevault.inventory.event;

import com.bytevault.inventory.config.RabbitMQConfig;
import com.bytevault.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventListener {

    private final InventoryService inventoryService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_CANCELLED)
    public void handleOrderCancelled(Map<String, Object> event) {
        log.info("[InventoryEventListener] Consumed order.cancelled event: {}", event);
        try {
            String orderIdStr = (String) event.get("orderId");
            if (orderIdStr != null) {
                UUID orderId = UUID.fromString(orderIdStr);
                inventoryService.releaseStockForOrder(orderId);
                log.info("[InventoryEventListener] Successfully released inventory for cancelled order: {}", orderId);
            }
        } catch (Exception e) {
            log.error("[InventoryEventListener] Error releasing inventory on order.cancelled: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAYMENT_FAILED)
    public void handlePaymentFailed(Map<String, Object> event) {
        log.info("[InventoryEventListener] Consumed payment.failed event: {}", event);
        try {
            String orderIdStr = (String) event.get("orderId");
            if (orderIdStr != null) {
                UUID orderId = UUID.fromString(orderIdStr);
                inventoryService.releaseStockForOrder(orderId);
                log.info("[InventoryEventListener] Successfully released inventory for failed payment order: {}", orderId);
            }
        } catch (Exception e) {
            log.error("[InventoryEventListener] Error releasing inventory on payment.failed: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ORDER_PAID)
    public void handleOrderPaid(Map<String, Object> event) {
        log.info("[InventoryEventListener] Consumed order.paid event: {}", event);
        try {
            String orderIdStr = (String) event.get("orderId");
            if (orderIdStr != null) {
                UUID orderId = UUID.fromString(orderIdStr);
                inventoryService.commitStockForOrder(orderId);
                log.info("[InventoryEventListener] Successfully committed inventory for paid order: {}", orderId);
            }
        } catch (Exception e) {
            log.error("[InventoryEventListener] Error committing inventory on order.paid: {}", e.getMessage(), e);
        }
    }
}
