package com.bytevault.order;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.order.controller.OrderController;
import com.bytevault.order.dto.OrderItemResponse;
import com.bytevault.order.dto.OrderPaymentConfirmationRequest;
import com.bytevault.order.dto.OrderResponse;
import com.bytevault.order.entity.*;
import com.bytevault.order.repository.OrderItemRepository;
import com.bytevault.order.repository.OrderRepository;
import com.bytevault.order.repository.OutboxEventRepository;
import com.bytevault.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderPaymentSecurityAndOutboxTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OrderService orderService;
    private OrderController orderController;

    private final String gatewaySecret = "test_gateway_shared_secret_xyz";

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                outboxEventRepository,
                new ObjectMapper(),
                null, // productClient
                null  // inventoryClient
        );

        orderController = new OrderController(orderService);
        ReflectionTestUtils.setField(orderController, "gatewaySecret", gatewaySecret);
    }

    @Test
    @DisplayName("Valid internal mark-paid request -> Marks order PAID and creates outbox row atomically")
    void testMarkOrderPaid_ValidInternalRequest_Success() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal total = new BigDecimal("299.00");

        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-TEST-101")
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .orderType(OrderType.DIGITAL)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.findByAggregateIdAndEventType(orderId, "order.paid")).thenReturn(Optional.empty());

        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_rzp_123")
                .razorpayOrderId("order_rzp_456")
                .amount(total)
                .currency("INR")
                .provider("RAZORPAY")
                .paymentTimestamp(LocalDateTime.now())
                .build();

        ResponseEntity<ApiResponse<OrderResponse>> response = orderController.markOrderPaidInternal(
                orderId, gatewaySecret, "ROLE_INTERNAL_SERVICE", req, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PAID", response.getBody().data().getStatus());
        assertEquals(OrderStatus.PAID, order.getStatus());

        // Verify outbox event created
        verify(outboxEventRepository, times(1)).save(argThat(event ->
                "ORDER".equals(event.getAggregateType())
                        && orderId.equals(event.getAggregateId())
                        && "order.paid".equals(event.getEventType())
                        && OutboxStatus.PENDING == event.getStatus()
        ));
    }

    @Test
    @DisplayName("Internal mark-paid with missing or invalid gateway secret -> 403 Forbidden")
    void testMarkOrderPaid_InvalidGatewaySecret_Forbidden() {
        UUID orderId = UUID.randomUUID();
        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_123")
                .amount(BigDecimal.TEN)
                .build();

        // Invalid secret
        ResponseEntity<ApiResponse<OrderResponse>> response = orderController.markOrderPaidInternal(
                orderId, "wrong_secret", "ROLE_INTERNAL_SERVICE", req, null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Internal mark-paid succeeds when called with valid gateway secret in CUSTOMER request context")
    void testMarkOrderPaid_CustomerContextWithValidSecret_Success() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-CUST-CTX")
                .userId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .orderType(OrderType.DIGITAL)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.findByAggregateIdAndEventType(orderId, "order.paid")).thenReturn(Optional.empty());

        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_123")
                .amount(new BigDecimal("100.00"))
                .build();

        // Customer-authenticated context forwarded across Feign, but carrying valid gatewaySecret
        ResponseEntity<ApiResponse<OrderResponse>> response = orderController.markOrderPaidInternal(
                orderId, gatewaySecret, "ROLE_CUSTOMER", req, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PAID", response.getBody().data().getStatus());
    }

    @Test
    @DisplayName("Internal vendor-breakdown rejects request with missing or invalid gateway secret (403)")
    void testGetVendorBreakdown_InvalidGatewaySecret_Forbidden() {
        UUID orderId = UUID.randomUUID();

        // Missing secret
        ResponseEntity<?> resMissing = orderController.getVendorBreakdown(orderId, null, null, null);
        assertEquals(HttpStatus.FORBIDDEN, resMissing.getStatusCode());

        // Invalid secret
        ResponseEntity<?> resInvalid = orderController.getVendorBreakdown(orderId, "wrong_secret", "ROLE_INTERNAL_SERVICE", null);
        assertEquals(HttpStatus.FORBIDDEN, resInvalid.getStatusCode());
    }

    @Test
    @DisplayName("Internal refund-cancel rejects request without gateway secret (403), succeeds with valid secret")
    void testRefundCancelInternal_Authorization() {
        UUID orderId = UUID.randomUUID();

        // Missing secret -> 403
        ResponseEntity<?> resMissing = orderController.refundCancelInternal(orderId, null, null, null);
        assertEquals(HttpStatus.FORBIDDEN, resMissing.getStatusCode());

        // Valid secret in customer context -> 200
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PAID)
                .orderType(OrderType.DIGITAL)
                .orderNumber("ORD-REFUND-AUTH")
                .totalAmount(BigDecimal.TEN)
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<ApiResponse<OrderResponse>> resValid = orderController.refundCancelInternal(
                orderId, gatewaySecret, "ROLE_CUSTOMER", null);
        assertEquals(HttpStatus.OK, resValid.getStatusCode());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    @DisplayName("Payment amount mismatch against authoritative order -> Throws BadRequestException")
    void testMarkOrderPaid_AmountMismatch_ThrowsBadRequest() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("500.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_123")
                .amount(new BigDecimal("400.00")) // Mismatch!
                .build();

        assertThrows(BadRequestException.class, () -> orderService.markOrderPaid(orderId, req));
        verify(outboxEventRepository, never()).save(any());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    @DisplayName("Illegal transition from CANCELLED order to PAID -> Throws BadRequestException")
    void testMarkOrderPaid_CancelledOrder_ThrowsBadRequest() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("100.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_123")
                .amount(new BigDecimal("100.00"))
                .build();

        assertThrows(BadRequestException.class, () -> orderService.markOrderPaid(orderId, req));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Idempotent replay of markOrderPaid for already PAID order -> Returns success without duplicate outbox")
    void testMarkOrderPaid_AlreadyPaid_IdempotentSuccess() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-ALREADY-PAID")
                .orderType(OrderType.DIGITAL)
                .status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("100.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_123")
                .amount(new BigDecimal("100.00"))
                .build();

        OrderResponse response = orderService.markOrderPaid(orderId, req);

        assertEquals("PAID", response.getStatus());
        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Outbox event persistence failure rolls back order state transition")
    void testMarkOrderPaid_OutboxFailure_ThrowsRuntimeException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-OUTBOX-FAIL")
                .userId(UUID.randomUUID())
                .orderType(OrderType.DIGITAL)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.findByAggregateIdAndEventType(orderId, "order.paid")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Outbox DB constraint error"))
                .when(outboxEventRepository).save(any());

        OrderPaymentConfirmationRequest req = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_123")
                .amount(new BigDecimal("100.00"))
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> orderService.markOrderPaid(orderId, req));
        assertTrue(ex.getMessage().contains("Failed to persist order payment event to outbox"));
    }
}
