package com.bytevault.fulfillment.event;

import com.bytevault.fulfillment.config.RabbitConfig;
import com.bytevault.fulfillment.service.FulfillmentService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventListener {

    public static final int MAX_RETRIES = 3;

    private final FulfillmentService fulfillmentService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.PAID_QUEUE)
    public void handleOrderPaid(Map<String, Object> event,
                                Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                @Header(name = "x-retry-count", required = false) Integer retryCountHeader) throws IOException {

        log.info("[OrderPaidEventListener] Processing order.paid message: tag={}, sanitizedPayload={}",
                deliveryTag, sanitizePayload(event));

        // 1. Verify status and extract identifiers
        String orderIdStr = event != null ? (String) event.get("orderId") : null;
        String status = event != null ? (String) event.get("status") : null;

        if (orderIdStr == null || (status != null && !"PAID".equalsIgnoreCase(status))) {
            log.error("[OrderPaidEventListener] Invalid or non-PAID order event received: status={}, orderId={}. Rejecting to DLQ without requeue.",
                    status, orderIdStr);
            // Permanent failure: NACK with requeue=false to forward to dead-letter exchange
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        UUID orderId;
        UUID userId;
        List<UUID> productIds = new ArrayList<>();

        try {
            orderId = UUID.fromString(orderIdStr);
            String userIdStr = (String) event.get("userId");
            userId = userIdStr != null ? UUID.fromString(userIdStr) : UUID.randomUUID();

            List<?> productIdsRaw = (List<?>) event.get("productIds");
            if (productIdsRaw != null) {
                for (Object pid : productIdsRaw) {
                    if (pid != null) {
                        productIds.add(UUID.fromString(pid.toString()));
                    }
                }
            }
        } catch (Exception ex) {
            log.error("[OrderPaidEventListener] Malformed UUIDs in payload for orderId={}: {}. Permanent failure, rejecting to DLQ.",
                    orderIdStr, ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        // 2. Perform digital fulfillment with idempotency guarantees
        try {
            fulfillmentService.fulfillDigitalOrder(orderId, userId, productIds);

            // Acknowledge only after fulfillment succeeds or has been idempotently confirmed
            channel.basicAck(deliveryTag, false);
            log.info("[OrderPaidEventListener] Digital fulfillment successfully processed and message ACKed: orderId={}, deliveryTag={}",
                    orderId, deliveryTag);

        } catch (IllegalArgumentException ex) {
            // Permanent business validation failure
            log.error("[OrderPaidEventListener] Permanent validation failure during fulfillment for orderId={}: {}. Rejecting to DLQ.",
                    orderId, ex.getMessage());
            channel.basicNack(deliveryTag, false, false);

        } catch (Exception ex) {
            // Transient failure (e.g. database connectivity, transient lock contention)
            int currentRetries = (retryCountHeader != null) ? retryCountHeader : 0;

            if (currentRetries < MAX_RETRIES) {
                int nextRetry = currentRetries + 1;
                log.warn("[OrderPaidEventListener] Transient failure fulfilling orderId={} (attempt {}/{}). Routing to retry queue with 5s delay: {}",
                        orderId, nextRetry, MAX_RETRIES, ex.getMessage());

                // Republish to delay/retry queue with incremented retry count
                rabbitTemplate.convertAndSend(
                        RabbitConfig.EXCHANGE,
                        RabbitConfig.RETRY_ROUTING_KEY,
                        event,
                        m -> {
                            m.getMessageProperties().setHeader("x-retry-count", nextRetry);
                            return m;
                        }
                );

                // ACK original message from main queue since it is safely handed off to retry queue
                channel.basicAck(deliveryTag, false);

            } else {
                log.error("[OrderPaidEventListener] CRITICAL: Max retries ({}) exhausted for orderId={}: {}. Rejecting to DLQ for operator inspection.",
                        MAX_RETRIES, orderId, ex.getMessage());
                // Exhausted retries: send directly to DLQ
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    /**
     * Sanitizes event map for safe logging without dumping PII or credentials.
     */
    public static Map<String, Object> sanitizePayload(Map<String, Object> event) {
        if (event == null) return Collections.emptyMap();
        Map<String, Object> copy = new HashMap<>(event);
        if (copy.containsKey("customerEmail")) {
            String email = String.valueOf(copy.get("customerEmail"));
            copy.put("customerEmail", maskEmail(email));
        }
        if (copy.containsKey("customerName")) {
            copy.put("customerName", "[PROTECTED]");
        }
        return copy;
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf("@");
        if (atIdx <= 1) return "*@" + email.substring(atIdx + 1);
        return email.charAt(0) + "***" + email.substring(atIdx);
    }
}
