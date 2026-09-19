package com.bytevault.order.service;

import com.bytevault.order.config.RabbitConfig;
import com.bytevault.order.entity.OutboxEvent;
import com.bytevault.order.entity.OutboxStatus;
import com.bytevault.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public static final int MAX_RETRIES = 5;
    private static final int BATCH_SIZE = 20;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void publishPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> readyEvents = outboxEventRepository.findReadyToPublish(
                OutboxStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE));

        if (readyEvents.isEmpty()) {
            return;
        }

        log.debug("[OutboxPublisher] Found {} pending outbox events ready to publish", readyEvents.size());

        for (OutboxEvent event : readyEvents) {
            processSingleOutboxEvent(event.getId());
        }
    }

    /**
     * Publishes an outbox event. To avoid holding DB connections and row locks
     * open during RabbitMQ network round-trips, claiming and completion are
     * performed in separate short local database transactions.
     */
    public void processSingleOutboxEvent(UUID eventId) {
        // Step 1: Claim row atomically across multiple application instances
        OutboxEvent claimedEvent = claimEvent(eventId);
        if (claimedEvent == null) {
            // Already claimed by another worker or not in PENDING status
            return;
        }

        // Step 2: Publish over network outside the database transaction
        try {
            Map<String, Object> payloadMap = objectMapper.readValue(
                    claimedEvent.getPayload(), new TypeReference<Map<String, Object>>() {});

            CorrelationData correlationData = new CorrelationData(claimedEvent.getId().toString());
            log.info("[OutboxPublisher] Publishing outbox event id={}, type={}, aggregateId={}",
                    claimedEvent.getId(), claimedEvent.getEventType(), claimedEvent.getAggregateId());

            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, claimedEvent.getEventType(), payloadMap, correlationData);

            // Step 3: Handle broker confirmation and returned messages
            boolean confirmed = false;
            String failureReason = null;

            try {
                CorrelationData.Confirm confirm = correlationData.getFuture().get(3, TimeUnit.SECONDS);
                if (confirm != null && confirm.isAck()) {
                    confirmed = true;
                } else {
                    failureReason = confirm != null ? "Broker NACK: " + confirm.getReason() : "Broker NACK: null confirm";
                    log.warn("[OutboxPublisher] Broker NACK for event id={}: {}", claimedEvent.getId(), failureReason);
                }
            } catch (TimeoutException te) {
                failureReason = "Broker confirm timed out after 3000ms";
                log.warn("[OutboxPublisher] Broker confirm timed out for event id={}", claimedEvent.getId());
            } catch (Exception ex) {
                failureReason = "Broker confirm failed: " + ex.getMessage();
                log.warn("[OutboxPublisher] Broker confirm execution error for event id={}: {}", claimedEvent.getId(), ex.getMessage());
            }

            // Check if message was returned as unroutable
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                confirmed = false;
                failureReason = "Unroutable message: code=" + returned.getReplyCode() + " text=" + returned.getReplyText();
                log.error("[OutboxPublisher] Message returned as unroutable by broker: eventId={}, reason={}",
                        claimedEvent.getId(), failureReason);
            }

            // Step 4: Record outcome in database
            if (confirmed) {
                recordPublishSuccess(claimedEvent.getId());
                log.info("[OutboxPublisher] Event successfully confirmed and marked PUBLISHED: id={}", claimedEvent.getId());
            } else {
                recordPublishFailure(claimedEvent.getId(), failureReason);
            }

        } catch (Exception e) {
            log.error("[OutboxPublisher] Unexpected error publishing outbox event id={}: {}", claimedEvent.getId(), e.getMessage());
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
                long backoffSeconds = Math.min(300, (long) Math.pow(2, nextRetries) * 2); // 4s, 8s, 16s, 32s...
                event.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                log.warn("[OutboxPublisher] Outbox event id={} retry #{} scheduled in {}s. Error: {}",
                        event.getId(), nextRetries, backoffSeconds, errorMessage);
            }
            outboxEventRepository.save(event);
        });
    }

    /**
     * Crash recovery: Resets events stuck in PROCESSING for more than 2 minutes
     * (e.g. from an ungraceful container termination during publication) back to PENDING.
     */
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
