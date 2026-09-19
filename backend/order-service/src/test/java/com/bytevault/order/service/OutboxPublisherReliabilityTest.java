package com.bytevault.order.service;

import com.bytevault.order.config.RabbitConfig;
import com.bytevault.order.entity.OutboxEvent;
import com.bytevault.order.entity.OutboxStatus;
import com.bytevault.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherReliabilityTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private OutboxPublisherService publisherService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisherService = new OutboxPublisherService(outboxEventRepository, rabbitTemplate, objectMapper);
    }

    @Test
    @DisplayName("Broker confirms publication (ACK) -> Outbox row marked as PUBLISHED")
    void testProcessEvent_BrokerAck_MarksPublished() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID())
                .eventType("order.paid")
                .payload("{\"orderId\":\"" + UUID.randomUUID() + "\",\"status\":\"PAID\"}")
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            CorrelationData.Confirm confirm = new CorrelationData.Confirm(true, null);
            cd.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("order.paid"), any(Map.class), any(CorrelationData.class));

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());
        verify(outboxEventRepository, atLeastOnce()).save(event);
    }

    @Test
    @DisplayName("Broker NACKs publication -> Event remains PENDING with incremented retry count and backoff")
    void testProcessEvent_BrokerNack_RetriesWithBackoff() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID())
                .eventType("order.paid")
                .payload("{\"orderId\":\"" + UUID.randomUUID() + "\",\"status\":\"PAID\"}")
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            CorrelationData.Confirm confirm = new CorrelationData.Confirm(false, "Queue quota exceeded");
            cd.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("order.paid"), any(Map.class), any(CorrelationData.class));

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getNextRetryAt());
        assertTrue(event.getLastError().contains("Broker NACK"));
    }

    @Test
    @DisplayName("Broker confirm times out -> Event NOT marked published, scheduled for retry")
    void testProcessEvent_ConfirmTimeout_Retries() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID())
                .eventType("order.paid")
                .payload("{\"orderId\":\"" + UUID.randomUUID() + "\",\"status\":\"PAID\"}")
                .status(OutboxStatus.PROCESSING)
                .retryCount(1)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        // Do not complete CorrelationData future -> causes TimeoutException on 3s get
        doNothing().when(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("order.paid"), any(Map.class), any(CorrelationData.class));

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(2, event.getRetryCount());
        assertTrue(event.getLastError().contains("timed out"));
    }

    @Test
    @DisplayName("Unroutable returned message -> Event NOT marked published, error recorded")
    void testProcessEvent_ReturnedMessage_RecordsFailure() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID())
                .eventType("order.paid")
                .payload("{\"orderId\":\"" + UUID.randomUUID() + "\",\"status\":\"PAID\"}")
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            ReturnedMessage returned = new ReturnedMessage(
                    new org.springframework.amqp.core.Message("{}".getBytes()), 312, "NO_ROUTE", RabbitConfig.EXCHANGE, "order.paid");
            cd.setReturned(returned);
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("order.paid"), any(Map.class), any(CorrelationData.class));

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertTrue(event.getLastError().contains("Unroutable message"));
    }

    @Test
    @DisplayName("Concurrent worker already claimed row -> Skipped without duplicate send")
    void testProcessEvent_ConcurrentWorkerClaim_Skips() {
        UUID eventId = UUID.randomUUID();
        // Return 0 updated rows indicating another worker claimed it first
        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(0);

        publisherService.processSingleOutboxEvent(eventId);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Map.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("Max retries exceeded (5) -> Marked as FAILED for operator intervention")
    void testProcessEvent_MaxRetriesExceeded_MarkedFailed() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID())
                .eventType("order.paid")
                .payload("{\"orderId\":\"" + UUID.randomUUID() + "\",\"status\":\"PAID\"}")
                .status(OutboxStatus.PROCESSING)
                .retryCount(4) // 4 retries already done, next will be 5
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq("order.paid"), any(Map.class), any(CorrelationData.class));

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(5, event.getRetryCount());
    }

    @Test
    @DisplayName("Stale processing events from crashed nodes are recovered to PENDING")
    void testRecoverStaleProcessingEvents() {
        OutboxEvent stale = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .status(OutboxStatus.PROCESSING)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(outboxEventRepository.findByStatus(OutboxStatus.PROCESSING))
                .thenReturn(List.of(stale));

        publisherService.recoverStaleProcessingEvents();

        assertEquals(OutboxStatus.PENDING, stale.getStatus());
        verify(outboxEventRepository, times(1)).save(stale);
    }
}
