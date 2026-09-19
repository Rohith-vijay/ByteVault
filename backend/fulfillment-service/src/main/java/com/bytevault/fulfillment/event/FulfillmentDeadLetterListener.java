package com.bytevault.fulfillment.event;

import com.bytevault.fulfillment.config.RabbitConfig;
import com.bytevault.fulfillment.dto.DlqIncidentDto;
import com.bytevault.fulfillment.service.DlqIncidentRegistry;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FulfillmentDeadLetterListener {

    private final DlqIncidentRegistry dlqIncidentRegistry;

    @RabbitListener(queues = RabbitConfig.DLQ_QUEUE)
    public void processDeadLetter(Map<String, Object> payload,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                  @Header(name = "x-death", required = false) List<Map<String, Object>> xDeathList) throws IOException {

        String incidentId = UUID.randomUUID().toString();
        String orderId = payload != null ? String.valueOf(payload.get("orderId")) : "UNKNOWN";

        String reason = "EXHAUSTED_OR_REJECTED";
        int deathCount = 1;
        if (xDeathList != null && !xDeathList.isEmpty()) {
            Map<String, Object> primaryDeath = xDeathList.get(0);
            if (primaryDeath.containsKey("reason")) {
                reason = String.valueOf(primaryDeath.get("reason"));
            }
            if (primaryDeath.containsKey("count")) {
                try {
                    deathCount = ((Number) primaryDeath.get("count")).intValue();
                } catch (Exception ignored) {}
            }
        }

        Map<String, Object> sanitized = OrderPaidEventListener.sanitizePayload(payload);

        log.error("[FulfillmentDLQ] INCIDENT ALERT: Message arrived in order.queue.paid.dlq! incidentId={}, orderId={}, deathCount={}, reason={}, payload={}",
                incidentId, orderId, deathCount, reason, sanitized);

        DlqIncidentDto incident = DlqIncidentDto.builder()
                .incidentId(incidentId)
                .orderId(orderId)
                .routingKey(RabbitConfig.DLQ_ROUTING_KEY)
                .reason(reason)
                .deathCount(deathCount)
                .timestamp(LocalDateTime.now())
                .sanitizedPayload(sanitized)
                .status("DEAD_LETTERED")
                .build();

        dlqIncidentRegistry.recordIncident(incident);

        // Acknowledge receipt of the dead-lettered message from DLQ once incident is recorded
        channel.basicAck(deliveryTag, false);
    }
}
