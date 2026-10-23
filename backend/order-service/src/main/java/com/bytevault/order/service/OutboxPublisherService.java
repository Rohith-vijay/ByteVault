package com.bytevault.order.service;

import com.bytevault.common.client.NotificationClient;
import com.bytevault.common.dto.notification.NotificationDto.OrderConfirmationEmailRequest;
import com.bytevault.order.client.FulfillmentClient;
import com.bytevault.order.entity.OutboxEvent;
import com.bytevault.order.entity.OutboxStatus;
import com.bytevault.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private FulfillmentClient fulfillmentClient;

    @Autowired(required = false)
    private NotificationClient notificationClient;

    @Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    public static final int MAX_RETRIES = 20;
    private static final int BATCH_SIZE = 20;

    public void setFulfillmentClient(FulfillmentClient fulfillmentClient) {
        this.fulfillmentClient = fulfillmentClient;
    }

    public void setNotificationClient(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> readyEvents = outboxEventRepository.findReadyToPublish(
                OutboxStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE));

        if (readyEvents.isEmpty()) {
            return;
        }

        log.debug("[OutboxPublisher] Found {} pending outbox events ready to process", readyEvents.size());

        for (OutboxEvent event : readyEvents) {
            processSingleOutboxEvent(event.getId());
        }
    }

    /**
     * Processes an outbox event synchronously as internal reliability infrastructure.
     * Retries failed inter-service synchronous calls without external message brokers.
     */
    public void processSingleOutboxEvent(UUID eventId) {
        OutboxEvent claimedEvent = claimEvent(eventId);
        if (claimedEvent == null) {
            return;
        }

        try {
            Map<String, Object> payloadMap = objectMapper.readValue(
                    claimedEvent.getPayload(), new TypeReference<Map<String, Object>>() {});

            log.info("[OutboxPublisher] Processing outbox event id={}, type={}, aggregateId={}",
                    claimedEvent.getId(), claimedEvent.getEventType(), claimedEvent.getAggregateId());

            if ("order.paid".equalsIgnoreCase(claimedEvent.getEventType())) {
                UUID orderId = UUID.fromString((String) payloadMap.get("orderId"));
                UUID userId = UUID.fromString((String) payloadMap.get("userId"));

                List<?> rawProducts = (List<?>) payloadMap.get("productIds");
                List<UUID> productIds = rawProducts != null
                        ? rawProducts.stream().map(p -> UUID.fromString(p.toString())).collect(Collectors.toList())
                        : List.of();

                if (fulfillmentClient != null && !productIds.isEmpty()) {
                    log.info("[OutboxPublisher] Dispatching synchronous fulfillment for order {}", orderId);
                    fulfillmentClient.fulfillOrderInternal(gatewaySecret, "ROLE_INTERNAL_SERVICE", orderId, userId, productIds);
                }

                // Defensive notification dispatch
                if (notificationClient != null && payloadMap.containsKey("customerEmail")) {
                    try {
                        String email = (String) payloadMap.get("customerEmail");
                        String name = (String) payloadMap.get("customerName");
                        String orderNum = (String) payloadMap.get("orderNumber");
                        BigDecimal amount = payloadMap.get("totalAmount") != null ? new BigDecimal(payloadMap.get("totalAmount").toString()) : null;

                        notificationClient.sendOrderConfirmationEmail(OrderConfirmationEmailRequest.builder()
                                .toEmail(email)
                                .customerName(name)
                                .orderId(orderId)
                                .orderNumber(orderNum)
                                .totalAmount(amount)
                                .downloadLink(null)
                                .build());
                    } catch (Exception notifEx) {
                        log.warn("[OutboxPublisher] Notification dispatch failed (swallowed): {}", notifEx.getMessage());
                    }
                }
            }

            recordPublishSuccess(claimedEvent.getId());
            log.info("[OutboxPublisher] Event successfully processed and marked PUBLISHED: id={}", claimedEvent.getId());

        } catch (Exception e) {
            log.error("[OutboxPublisher] Error processing outbox event id={}: {}", claimedEvent.getId(), e.getMessage());
            recordPublishFailure(claimedEvent.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OutboxEvent claimEvent(UUID eventId) {
        int updated = outboxEventRepository.claimEventForProcessing(
                eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING);
        if (updated > 0) {
            return outboxEventRepository.findById(eventId).orElse(null);
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPublishSuccess(UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
            outboxEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPublishFailure(UUID eventId, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            int nextRetries = event.getRetryCount() + 1;
            event.setRetryCount(nextRetries);
            event.setLastError(errorMessage);

            if (nextRetries >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("[OutboxPublisher] CRITICAL: Outbox event id={} marked FAILED after {} attempts. Reason: {}",
                        event.getId(), nextRetries, errorMessage);
            } else {
                event.setStatus(OutboxStatus.PENDING);
                long backoffSeconds = Math.min(300, (long) Math.pow(2, nextRetries) * 2);
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                log.warn("[OutboxPublisher] Outbox event id={} retry #{} scheduled in {}s. Error: {}",
                        event.getId(), nextRetries, backoffSeconds, errorMessage);
            }
            outboxEventRepository.save(event);
        });
    }

    @Scheduled(fixedDelayString = "${app.outbox.stale-cleanup-ms:60000}")
    @Transactional
    public void recoverStaleProcessingEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        List<OutboxEvent> stale = outboxEventRepository.findByStatus(OutboxStatus.PROCESSING);
        for (OutboxEvent event : stale) {
            if (event.getCreatedAt() != null && event.getCreatedAt().isBefore(threshold)) {
                log.warn("[OutboxPublisher] Recovering stale PROCESSING event id={}, created at {}",
                        event.getId(), event.getCreatedAt());
                event.setStatus(OutboxStatus.PENDING);
                outboxEventRepository.save(event);
            }
        }
    }
}
