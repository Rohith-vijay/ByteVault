package com.bytevault.order.service;

import com.bytevault.order.entity.Order;
import com.bytevault.order.entity.OrderStatus;
import com.bytevault.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderExpiryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderExpiryService orderExpiryService;

    @Test
    @DisplayName("Stale pending orders older than cutoff are moved to CANCELLED")
    void testExpireStalePendingOrders_ExpiresMatchingOrders() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PENDING)
                .build();
        order.setCreatedAt(LocalDateTime.now().minusMinutes(45));

        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        orderExpiryService.expireStalePendingOrders();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("No stale pending orders -> no repository mutations performed")
    void testExpireStalePendingOrders_NoMatchingOrders() {
        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        orderExpiryService.expireStalePendingOrders();

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Multiple stale pending orders are all expired to CANCELLED in one run")
    void testExpireStalePendingOrders_MultipleOrders() {
        Order order1 = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PENDING).build();
        Order order2 = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PENDING).build();

        when(orderRepository.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(order1, order2));

        orderExpiryService.expireStalePendingOrders();

        assertEquals(OrderStatus.CANCELLED, order1.getStatus());
        assertEquals(OrderStatus.CANCELLED, order2.getStatus());
        verify(orderRepository, times(2)).save(any(Order.class));
    }
}
