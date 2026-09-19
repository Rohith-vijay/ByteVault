package com.bytevault.fulfillment.event;

import com.bytevault.fulfillment.config.RabbitConfig;
import com.bytevault.fulfillment.controller.DlqManagementController;
import com.bytevault.fulfillment.dto.DlqIncidentDto;
import com.bytevault.fulfillment.service.DlqIncidentRegistry;
import com.bytevault.fulfillment.service.FulfillmentService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FulfillmentReliabilityAndIdempotencyTest {

    @Mock
    private FulfillmentService fulfillmentService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Channel channel;

    private OrderPaidEventListener listener;
    private DlqIncidentRegistry dlqIncidentRegistry;
    private FulfillmentDeadLetterListener dlqListener;
    private DlqManagementController dlqController;

    private final String gatewaySecret = "test_gateway_secret_123";

    @BeforeEach
    void setUp() {
        listener = new OrderPaidEventListener(fulfillmentService, rabbitTemplate);
        dlqIncidentRegistry = new DlqIncidentRegistry();
        dlqListener = new FulfillmentDeadLetterListener(dlqIncidentRegistry);
        dlqController = new DlqManagementController(dlqIncidentRegistry, rabbitTemplate);
        ReflectionTestUtils.setField(dlqController, "gatewaySecret", gatewaySecret);
    }

    @Test
    @DisplayName("Valid order.paid event -> Successfully fulfilled and message ACKed")
    void testHandleOrderPaid_Success_AcksMessage() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("userId", userId.toString());
        event.put("productIds", List.of(productId.toString()));
        event.put("status", "PAID");
        event.put("customerEmail", "customer@example.com");

        long deliveryTag = 101L;

        listener.handleOrderPaid(event, channel, deliveryTag, 0);

        // Verify fulfillment was invoked
        verify(fulfillmentService, times(1)).fulfillDigitalOrder(eq(orderId), eq(userId), anyList());
        // Verify manual ACK was issued
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Invalid or missing orderId / non-PAID status -> NACKed to DLQ without requeue (permanent error)")
    void testHandleOrderPaid_InvalidStatus_NacksToDlq() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", UUID.randomUUID().toString());
        event.put("status", "PAYMENT_FAILED"); // Not PAID

        long deliveryTag = 102L;

        listener.handleOrderPaid(event, channel, deliveryTag, 0);

        verify(fulfillmentService, never()).fulfillDigitalOrder(any(), any(), any());
        // Verify permanent rejection to DLQ (requeue = false)
        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Malformed UUIDs in payload -> NACKed to DLQ without requeue")
    void testHandleOrderPaid_MalformedUuid_NacksToDlq() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", "not-a-valid-uuid");
        event.put("status", "PAID");

        long deliveryTag = 103L;

        listener.handleOrderPaid(event, channel, deliveryTag, 0);

        verify(fulfillmentService, never()).fulfillDigitalOrder(any(), any(), any());
        verify(channel, times(1)).basicNack(deliveryTag, false, false);
    }

    @Test
    @DisplayName("Transient database exception under retry limit -> Routed to retry queue, original message ACKed")
    void testHandleOrderPaid_TransientFailure_RoutesToRetryQueue() throws IOException {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("userId", userId.toString());
        event.put("productIds", List.of(UUID.randomUUID().toString()));
        event.put("status", "PAID");

        doThrow(new RuntimeException("Transient DB connection timeout"))
                .when(fulfillmentService).fulfillDigitalOrder(any(), any(), any());

        long deliveryTag = 104L;

        // Attempt 0 -> Should route to retry queue with attempt 1
        listener.handleOrderPaid(event, channel, deliveryTag, 0);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.RETRY_ROUTING_KEY),
                eq(event),
                any(MessagePostProcessor.class)
        );
        // Original message must be ACKed from main queue so it doesn't loop tightly
        verify(channel, times(1)).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Transient failure exceeding max retries (3) -> NACKed to DLQ without requeue")
    void testHandleOrderPaid_MaxRetriesExceeded_NacksToDlq() throws IOException {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("userId", UUID.randomUUID().toString());
        event.put("productIds", List.of(UUID.randomUUID().toString()));
        event.put("status", "PAID");

        doThrow(new RuntimeException("Database persistently unavailable"))
                .when(fulfillmentService).fulfillDigitalOrder(any(), any(), any());

        long deliveryTag = 105L;

        // Already at max retries (3)
        listener.handleOrderPaid(event, channel, deliveryTag, OrderPaidEventListener.MAX_RETRIES);

        // Should NOT publish to retry queue again
        verify(rabbitTemplate, never()).convertAndSend(anyString(), eq(RabbitConfig.RETRY_ROUTING_KEY), any(Map.class), any(MessagePostProcessor.class));
        // Must reject to DLQ
        verify(channel, times(1)).basicNack(deliveryTag, false, false);
    }

    @Test
    @DisplayName("Permanent validation failure during fulfillment -> Rejected directly to DLQ")
    void testHandleOrderPaid_PermanentFulfillmentFailure_NacksToDlq() throws IOException {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId.toString());
        event.put("userId", UUID.randomUUID().toString());
        event.put("productIds", List.of(UUID.randomUUID().toString()));
        event.put("status", "PAID");

        doThrow(new IllegalArgumentException("Product no longer available in store catalog"))
                .when(fulfillmentService).fulfillDigitalOrder(any(), any(), any());

        long deliveryTag = 106L;

        listener.handleOrderPaid(event, channel, deliveryTag, 0);

        verify(channel, times(1)).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("DLQ listener records incident and alerts operator without leaking PII")
    void testDeadLetterListener_RecordsIncident() throws IOException {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("status", "PAID");
        payload.put("customerEmail", "john.doe@company.com");
        payload.put("customerName", "John Doe");

        List<Map<String, Object>> xDeath = List.of(
                Map.of("reason", "rejected", "count", 3L, "queue", RabbitConfig.PAID_QUEUE)
        );

        long deliveryTag = 201L;

        dlqListener.processDeadLetter(payload, channel, deliveryTag, xDeath);

        verify(channel, times(1)).basicAck(deliveryTag, false);

        List<DlqIncidentDto> incidents = dlqIncidentRegistry.getAllIncidents();
        assertEquals(1, incidents.size());
        DlqIncidentDto incident = incidents.get(0);
        assertEquals(orderId.toString(), incident.getOrderId());
        assertEquals("rejected", incident.getReason());
        assertEquals(3, incident.getDeathCount());
        // Verify email was masked
        assertEquals("j***@company.com", incident.getSanitizedPayload().get("customerEmail"));
        assertEquals("[PROTECTED]", incident.getSanitizedPayload().get("customerName"));
    }

    @Test
    @DisplayName("Operator safely replays DLQ incident via internal API")
    void testDlqReplay_RepublishesToOrderPaid() {
        UUID orderId = UUID.randomUUID();
        String incidentId = UUID.randomUUID().toString();
        DlqIncidentDto incident = DlqIncidentDto.builder()
                .incidentId(incidentId)
                .orderId(orderId.toString())
                .status("DEAD_LETTERED")
                .sanitizedPayload(Map.of("orderId", orderId.toString(), "status", "PAID"))
                .build();
        dlqIncidentRegistry.recordIncident(incident);

        ResponseEntity<?> response = dlqController.replayDlqIncident(
                incidentId, gatewaySecret, "ROLE_INTERNAL_SERVICE", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.ROUTING_KEY), any(Map.class));

        DlqIncidentDto updated = dlqIncidentRegistry.getIncident(incidentId).orElseThrow();
        assertEquals("REPLAYED", updated.getStatus());
    }
}
