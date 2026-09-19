package com.bytevault.shipping.event;

import com.bytevault.shipping.config.RabbitMQConfig;
import com.bytevault.shipping.entity.Shipment;
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
public class ShippingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishShipmentCreated(Shipment shipment) {
        publishEvent(RabbitMQConfig.ROUTING_KEY_SHIPMENT_CREATED, shipment);
    }

    public void publishShipmentDispatched(Shipment shipment) {
        publishEvent(RabbitMQConfig.ROUTING_KEY_SHIPMENT_DISPATCHED, shipment);
    }

    public void publishShipmentDelivered(Shipment shipment) {
        publishEvent(RabbitMQConfig.ROUTING_KEY_SHIPMENT_DELIVERED, shipment);
    }

    private void publishEvent(String routingKey, Shipment shipment) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("shipmentId", shipment.getId());
            payload.put("orderId", shipment.getOrderId());
            payload.put("trackingNumber", shipment.getTrackingNumber());
            payload.put("carrier", shipment.getCarrier());
            payload.put("status", shipment.getStatus());
            payload.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(RabbitMQConfig.SHIPPING_EXCHANGE, routingKey, payload);
            log.info("[ShippingEventPublisher] Published {} event for orderId={}, trackingNumber={}",
                    routingKey, shipment.getOrderId(), shipment.getTrackingNumber());
        } catch (Exception e) {
            log.warn("[ShippingEventPublisher] Failed to publish {} event for orderId={}: {}",
                    routingKey, shipment.getOrderId(), e.getMessage());
        }
    }
}
