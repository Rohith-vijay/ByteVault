package com.bytevault.order.integration;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.order.dto.OrderResponse;
import com.bytevault.order.entity.Order;
import com.bytevault.order.entity.OrderStatus;
import com.bytevault.order.entity.OrderType;
import com.bytevault.order.repository.OrderRepository;
import com.bytevault.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderStateMachineAndIdorSecurityTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private UUID userA;
    private UUID userB;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    @DisplayName("IDOR Protection: User B cannot retrieve User A's order")
    void testIdorProtection_UserCannotAccessOtherOrders() {
        Order order = Order.builder()
                .id(orderId)
                .userId(userA)
                .orderNumber("ORD-100")
                .status(OrderStatus.PAID)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.valueOf(99.0))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class, () ->
                orderService.getOrderById(orderId, userB, false));
    }

    @Test
    @DisplayName("Admin can retrieve any user's order")
    void testAdminAccessToUserOrder() {
        Order order = Order.builder()
                .id(orderId)
                .userId(userA)
                .orderNumber("ORD-100")
                .status(OrderStatus.PAID)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.valueOf(99.0))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse resp = orderService.getOrderById(orderId, userB, true);

        assertNotNull(resp);
        assertEquals("ORD-100", resp.getOrderNumber());
    }

    @Test
    @DisplayName("Order state machine rejects illegal transition: DELIVERED -> PENDING")
    void testIllegalStateTransition_DeliveredToPending() {
        Order order = Order.builder()
                .id(orderId)
                .userId(userA)
                .status(OrderStatus.DELIVERED)
                .orderType(OrderType.PHYSICAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                orderService.updateOrderStatus(orderId, OrderStatus.PENDING));
    }

    @Test
    @DisplayName("Order state machine allows valid progression: PENDING -> PAID -> FULFILLED")
    void testValidStateProgression() {
        Order order = Order.builder()
                .id(orderId)
                .userId(userA)
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse paid = orderService.updateOrderStatus(orderId, OrderStatus.PAID);
        assertEquals(OrderStatus.PAID.name(), paid.getStatus());

        OrderResponse fulfilled = orderService.updateOrderStatus(orderId, OrderStatus.FULFILLED);
        assertEquals(OrderStatus.FULFILLED.name(), fulfilled.getStatus());
    }

    @Test
    @DisplayName("IDOR Protection: User B cannot cancel User A's order")
    void testIdorProtection_UserCannotCancelOtherOrders() {
        Order order = Order.builder()
                .id(orderId)
                .userId(userA)
                .orderNumber("ORD-100")
                .status(OrderStatus.PENDING)
                .orderType(OrderType.PHYSICAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class, () ->
                orderService.cancelOrder(orderId, userB, false, "Unauthorized cancellation"));
    }
}
