package com.bytevault.fulfillment.event;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.fulfillment.controller.FulfillmentController;
import com.bytevault.fulfillment.service.FulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FulfillmentReliabilityAndIdempotencyTest {

    @Mock
    private FulfillmentService fulfillmentService;

    @InjectMocks
    private FulfillmentController fulfillmentController;

    private final String gatewaySecret = "platform_default_gateway_shared_secret";

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(fulfillmentController, "gatewaySecret", gatewaySecret);
    }

    @Test
    @DisplayName("Synchronous internal fulfillment execution succeeds and returns 200 OK")
    void testFulfillOrderInternal_Success() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        doNothing().when(fulfillmentService).fulfillDigitalOrder(eq(orderId), eq(userId), eq(List.of(productId)));

        ResponseEntity<ApiResponse<Void>> response = fulfillmentController.fulfillOrderInternal(
                gatewaySecret, orderId, userId, List.of(productId));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        verify(fulfillmentService, times(1)).fulfillDigitalOrder(orderId, userId, List.of(productId));
    }

    @Test
    @DisplayName("Synchronous internal fulfillment rejects invalid or missing gateway secret (403)")
    void testFulfillOrderInternal_InvalidGatewaySecret_Forbidden() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Missing secret
        ResponseEntity<ApiResponse<Void>> resMissing = fulfillmentController.fulfillOrderInternal(
                null, orderId, userId, List.of(productId));
        assertEquals(HttpStatus.FORBIDDEN, resMissing.getStatusCode());

        // Invalid secret
        ResponseEntity<ApiResponse<Void>> resInvalid = fulfillmentController.fulfillOrderInternal(
                "wrong_secret", orderId, userId, List.of(productId));
        assertEquals(HttpStatus.FORBIDDEN, resInvalid.getStatusCode());

        verify(fulfillmentService, never()).fulfillDigitalOrder(any(), any(), any());
    }

    @Test
    @DisplayName("Duplicate synchronous fulfillment requests execute idempotently")
    void testFulfillOrderInternal_IdempotentExecution() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        doNothing().when(fulfillmentService).fulfillDigitalOrder(eq(orderId), eq(userId), eq(List.of(productId)));

        // Call 1
        ResponseEntity<ApiResponse<Void>> response1 = fulfillmentController.fulfillOrderInternal(
                gatewaySecret, orderId, userId, List.of(productId));
        // Call 2 (retry or replay)
        ResponseEntity<ApiResponse<Void>> response2 = fulfillmentController.fulfillOrderInternal(
                gatewaySecret, orderId, userId, List.of(productId));

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        verify(fulfillmentService, times(2)).fulfillDigitalOrder(orderId, userId, List.of(productId));
    }

    @Test
    @DisplayName("Fulfillment service exception is properly thrown on synchronous failure")
    void testFulfillOrderInternal_FailurePropagated() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        doThrow(new RuntimeException("Database error during digital fulfillment"))
                .when(fulfillmentService).fulfillDigitalOrder(eq(orderId), eq(userId), any());

        assertThrows(RuntimeException.class, () ->
                fulfillmentController.fulfillOrderInternal(gatewaySecret, orderId, userId, List.of(productId)));
    }

    @Test
    @DisplayName("Internal revoke-by-order succeeds with valid gateway secret and returns revoked count")
    void testRevokeEntitlementsByOrderInternal_Success() {
        UUID orderId = UUID.randomUUID();
        when(fulfillmentService.revokeEntitlementsByOrderId(orderId)).thenReturn(2);

        ResponseEntity<ApiResponse<java.util.Map<String, Object>>> response =
                fulfillmentController.revokeEntitlementsByOrderInternal(gatewaySecret, "ROLE_INTERNAL_SERVICE", orderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals(2, response.getBody().data().get("revokedCount"));
        assertEquals(orderId, response.getBody().data().get("orderId"));
        verify(fulfillmentService, times(1)).revokeEntitlementsByOrderId(orderId);
    }

    @Test
    @DisplayName("Internal revoke-by-order rejects missing or invalid gateway secret with 403 Forbidden")
    void testRevokeEntitlementsByOrderInternal_InvalidGatewaySecret_Forbidden() {
        UUID orderId = UUID.randomUUID();

        // Missing secret
        ResponseEntity<ApiResponse<java.util.Map<String, Object>>> resMissing =
                fulfillmentController.revokeEntitlementsByOrderInternal(null, "ROLE_INTERNAL_SERVICE", orderId);
        assertEquals(HttpStatus.FORBIDDEN, resMissing.getStatusCode());

        // Invalid secret
        ResponseEntity<ApiResponse<java.util.Map<String, Object>>> resInvalid =
                fulfillmentController.revokeEntitlementsByOrderInternal("wrong_secret", "ROLE_INTERNAL_SERVICE", orderId);
        assertEquals(HttpStatus.FORBIDDEN, resInvalid.getStatusCode());

        verify(fulfillmentService, never()).revokeEntitlementsByOrderId(any());
    }
}
