package com.bytevault.order.service;

import com.bytevault.order.client.FulfillmentClient;
import com.bytevault.order.entity.OutboxEvent;
import com.bytevault.order.entity.OutboxStatus;
import com.bytevault.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherReliabilityTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private FulfillmentClient fulfillmentClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxPublisherService publisherService;

    @BeforeEach
    void setUp() {
        publisherService = new OutboxPublisherService(outboxEventRepository, objectMapper);
        publisherService.setFulfillmentClient(fulfillmentClient);
    }

    @Test
    @DisplayName("Synchronous fulfillment dispatch succeeds -> Outbox row marked as PUBLISHED")
    void testProcessSingleOutboxEvent_Success() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        String payload = String.format("{\"orderId\":\"%s\",\"userId\":\"%s\",\"productIds\":[\"%s\"]}",
                orderId, userId, productId);

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateId(orderId)
                .eventType("order.paid")
                .payload(payload)
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());
        verify(fulfillmentClient, times(1)).fulfillOrderInternal(any(), any(), eq(orderId), eq(userId), any());
    }

    @Test
    @DisplayName("Fulfillment client failure -> Outbox row scheduled for retry (PENDING with backoff)")
    void testProcessSingleOutboxEvent_RetryOnFailure() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        String payload = String.format("{\"orderId\":\"%s\",\"userId\":\"%s\",\"productIds\":[\"%s\"]}",
                orderId, userId, productId);

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateId(orderId)
                .eventType("order.paid")
                .payload(payload)
                .status(OutboxStatus.PROCESSING)
                .retryCount(1)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        doThrow(new RuntimeException("Fulfillment service temporarily down"))
                .when(fulfillmentClient).fulfillOrderInternal(any(), any(), any(), any(), any());

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(2, event.getRetryCount());
        assertNotNull(event.getNextRetryAt());
        assertTrue(event.getLastError().contains("Fulfillment service temporarily down"));
    }

    @Test
    @DisplayName("Exceeding MAX_RETRIES transitions Outbox event to FAILED (Dead Letter)")
    void testProcessSingleOutboxEvent_ExceedsMaxRetries_TransitionsToFailed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UUID productId = UUID.randomUUID();
        String payload = String.format("{\"orderId\":\"%s\",\"userId\":\"%s\",\"productIds\":[\"%s\"]}",
                orderId, userId, productId);

        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateId(orderId)
                .eventType("order.paid")
                .payload(payload)
                .status(OutboxStatus.PROCESSING)
                .retryCount(OutboxPublisherService.MAX_RETRIES - 1)
                .build();

        when(outboxEventRepository.claimEventForProcessing(eventId, OutboxStatus.PENDING, OutboxStatus.PROCESSING))
                .thenReturn(1);
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        doThrow(new RuntimeException("Unrecoverable downstream error"))
                .when(fulfillmentClient).fulfillOrderInternal(any(), any(), any(), any(), any());

        publisherService.processSingleOutboxEvent(eventId);

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(OutboxPublisherService.MAX_RETRIES, event.getRetryCount());
    }

    @Test
    @DisplayName("Crash recovery: Stale PROCESSING events are reset to PENDING")
    void testRecoverStaleProcessingEvents() {
        OutboxEvent stale = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .status(OutboxStatus.PROCESSING)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(outboxEventRepository.findByStatus(OutboxStatus.PROCESSING)).thenReturn(List.of(stale));

        publisherService.recoverStaleProcessingEvents();

        assertEquals(OutboxStatus.PENDING, stale.getStatus());
        verify(outboxEventRepository, times(1)).save(stale);
    }
}
