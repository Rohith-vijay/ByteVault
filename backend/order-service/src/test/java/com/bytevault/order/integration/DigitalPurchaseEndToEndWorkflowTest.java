package com.bytevault.order.integration;

import com.bytevault.order.client.ProductClient;
import com.bytevault.order.dto.CreateOrderRequest;
import com.bytevault.order.dto.OrderItemRequest;
import com.bytevault.order.dto.OrderResponse;
import com.bytevault.order.dto.ProductSummaryResponse;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DigitalPurchaseEndToEndWorkflowTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderService orderService;

    private UUID userId;
    private UUID digitalProductId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        digitalProductId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Digital order workflow: Validates product, creates order, and dispatches OrderCreatedEvent")
    void testDigitalOrderWorkflow() {
        ProductSummaryResponse digitalProduct = ProductSummaryResponse.builder()
                .id(digitalProductId)
                .name("Distributed Systems Design Patterns")
                .price(BigDecimal.valueOf(149.99))
                .productType("DIGITAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(digitalProductId)).thenReturn(digitalProduct);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(digitalProductId)
                        .quantity(1)
                        .build()))
                .customerEmail("engineer@bytevault.com")
                .customerName("Engineer Dave")
                .build();

        OrderResponse orderResponse = orderService.createOrder(userId, "engineer@bytevault.com", "Engineer Dave", request);

        assertNotNull(orderResponse);
        assertEquals(OrderStatus.PENDING.name(), orderResponse.getStatus());
        assertEquals(OrderType.DIGITAL.name(), orderResponse.getOrderType());
        assertEquals(BigDecimal.valueOf(149.99), orderResponse.getTotalAmount());
        assertEquals(1, orderResponse.getItems().size());
        assertTrue(orderResponse.getItems().get(0).getIsDigital());

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(rabbitTemplate, times(1)).convertAndSend(eq("order.exchange"), eq("order.created"), any(Map.class));
    }
}
