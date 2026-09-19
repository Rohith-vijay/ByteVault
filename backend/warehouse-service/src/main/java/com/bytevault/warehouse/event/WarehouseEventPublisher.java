package com.bytevault.warehouse.event;

import com.bytevault.warehouse.config.RabbitMQConfig;
import com.bytevault.warehouse.entity.PickList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishPickListCreated(PickList pickList) {
        publishEvent(RabbitMQConfig.ROUTING_KEY_PICKLIST_CREATED, pickList);
    }

    public void publishPickListPacked(PickList pickList) {
        publishEvent(RabbitMQConfig.ROUTING_KEY_PICKLIST_PACKED, pickList);
    }

    private void publishEvent(String routingKey, PickList pickList) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("pickListId", pickList.getId());
            payload.put("orderId", pickList.getOrderId());
            payload.put("warehouseCode", pickList.getWarehouseCode());
            payload.put("status", pickList.getStatus());
            payload.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(RabbitMQConfig.WAREHOUSE_EXCHANGE, routingKey, payload);
            log.info("[WarehouseEventPublisher] Published {} event for orderId={}", routingKey, pickList.getOrderId());
        } catch (Exception e) {
            log.warn("[WarehouseEventPublisher] Failed to publish {} for orderId={}: {}",
                    routingKey, pickList.getOrderId(), e.getMessage());
        }
    }
}
