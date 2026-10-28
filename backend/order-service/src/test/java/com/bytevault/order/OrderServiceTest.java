package com.bytevault.order;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private com.bytevault.order.repository.OrderItemRepository orderItemRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private com.bytevault.order.client.InventoryClient inventoryClient;

    @Mock
    private com.bytevault.order.client.FulfillmentRevokeClient fulfillmentRevokeClient;

    @InjectMocks
    private OrderService orderService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        orderService.setFulfillmentRevokeClient(fulfillmentRevokeClient);
    }

    @Test
    @DisplayName("Digital order creation with verified PUBLISHED price snapshots from product-service")
    void testCreateDigitalOrder_PublishedProduct() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        ProductSummaryResponse productSummary = ProductSummaryResponse.builder()
                .id(productId)
                .name("Clean Architecture E-Book")
                .price(BigDecimal.valueOf(499.00))
                .productType("DIGITAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(productSummary);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(1)
                        .build()))
                .customerEmail("buyer@bytevault.com")
                .customerName("John Doe")
                .build();

        OrderResponse response = orderService.createOrder(userId, "buyer@bytevault.com", "John Doe", request);

        assertNotNull(response);
        assertEquals(OrderStatus.PENDING.name(), response.getStatus());
        assertEquals(OrderType.DIGITAL.name(), response.getOrderType());
        assertEquals(BigDecimal.valueOf(499.00), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
        assertEquals("Clean Architecture E-Book", response.getItems().get(0).getProductName());
        assertTrue(response.getItems().get(0).getIsDigital());

        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Digital order creation with legacy ACTIVE product status")
    void testCreateDigitalOrder_ActiveProduct() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        ProductSummaryResponse productSummary = ProductSummaryResponse.builder()
                .id(productId)
                .name("Legacy Active E-Book")
                .price(BigDecimal.valueOf(199.00))
                .productType("DIGITAL")
                .status("ACTIVE")
                .build();

        when(productClient.getProductById(productId)).thenReturn(productSummary);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(1)
                        .build()))
                .customerEmail("buyer@bytevault.com")
                .customerName("John Doe")
                .build();

        OrderResponse response = orderService.createOrder(userId, "buyer@bytevault.com", "John Doe", request);

        assertNotNull(response);
        assertEquals(OrderStatus.PENDING.name(), response.getStatus());
        assertEquals(BigDecimal.valueOf(199.00), response.getTotalAmount());
    }

    @Test
    @DisplayName("Draft product rejection -> Throws BadRequestException")
    void testCreateOrder_DraftProduct_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        ProductSummaryResponse draftProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Unpublished Course")
                .price(BigDecimal.valueOf(299.00))
                .productType("DIGITAL")
                .status("DRAFT")
                .build();

        when(productClient.getProductById(productId)).thenReturn(draftProduct);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(1)
                        .build()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "user@bytevault.com", "User", request));

        assertTrue(ex.getMessage().contains("inactive"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Inactive product rejection -> Throws BadRequestException")
    void testCreateOrder_InactiveProduct_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        ProductSummaryResponse inactiveProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Legacy Software")
                .price(BigDecimal.valueOf(199.00))
                .productType("DIGITAL")
                .status("INACTIVE")
                .build();

        when(productClient.getProductById(productId)).thenReturn(inactiveProduct);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(productId)
                        .quantity(1)
                        .build()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "user@bytevault.com", "User", request));

        assertTrue(ex.getMessage().contains("inactive"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Nonexistent product rejection -> Throws ResourceNotFoundException")
    void testCreateOrder_NonexistentProduct_ThrowsResourceNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID nonExistentProductId = UUID.randomUUID();

        when(productClient.getProductById(nonExistentProductId)).thenReturn(null);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(nonExistentProductId)
                        .quantity(1)
                        .build()))
                .build();

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () ->
                orderService.createOrder(userId, "user@bytevault.com", "User", request));

        assertTrue(ex.getMessage().contains("Product not found"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Physical product order creation -> OrderType is set to PHYSICAL")
    void testCreatePhysicalOrder() {
        UUID userId = UUID.randomUUID();
        UUID physicalProductId = UUID.randomUUID();

        ProductSummaryResponse physicalProduct = ProductSummaryResponse.builder()
                .id(physicalProductId)
                .name("ByteVault Hardcover Book")
                .price(BigDecimal.valueOf(899.00))
                .productType("PHYSICAL")
                .status("ACTIVE")
                .build();

        when(productClient.getProductById(physicalProductId)).thenReturn(physicalProduct);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        org.springframework.test.util.ReflectionTestUtils.setField(orderService, "physicalProductsEnabled", true);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(physicalProductId)
                        .quantity(1)
                        .build()))
                .shippingAddress("123 Tech Park, Silicon Valley, CA")
                .build();

        OrderResponse response = orderService.createOrder(userId, "buyer@bytevault.com", "John Doe", request);

        assertNotNull(response);
        assertEquals(OrderType.PHYSICAL.name(), response.getOrderType());
        assertEquals(BigDecimal.valueOf(899.00), response.getTotalAmount());
        assertFalse(response.getItems().get(0).getIsDigital());
    }

    @Test
    @DisplayName("Physical order creation with insufficient inventory throws BadRequestException")
    void testCreatePhysicalOrder_InsufficientStock_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID physicalProductId = UUID.randomUUID();

        ProductSummaryResponse physicalProduct = ProductSummaryResponse.builder()
                .id(physicalProductId)
                .name("ByteVault Hardcover Book")
                .price(BigDecimal.valueOf(899.00))
                .productType("PHYSICAL")
                .status("ACTIVE")
                .build();

        when(productClient.getProductById(physicalProductId)).thenReturn(physicalProduct);
        when(inventoryClient.reserveStock(eq(physicalProductId), eq(1), any()))
                .thenReturn(com.bytevault.common.api.ApiResponse.error("Insufficient stock", 400));

        org.springframework.test.util.ReflectionTestUtils.setField(orderService, "physicalProductsEnabled", true);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(physicalProductId)
                        .quantity(1)
                        .build()))
                .shippingAddress("123 Tech Park, Silicon Valley, CA")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "buyer@bytevault.com", "John Doe", request));

        assertTrue(ex.getMessage().contains("Insufficient inventory"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Physical product order creation rejected when physicalProductsEnabled is false")
    void testCreatePhysicalOrder_DisabledByDefault_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID physicalProductId = UUID.randomUUID();

        ProductSummaryResponse physicalProduct = ProductSummaryResponse.builder()
                .id(physicalProductId)
                .name("ByteVault Hardcover Book")
                .price(BigDecimal.valueOf(899.00))
                .productType("PHYSICAL")
                .status("ACTIVE")
                .build();

        when(productClient.getProductById(physicalProductId)).thenReturn(physicalProduct);

        org.springframework.test.util.ReflectionTestUtils.setField(orderService, "physicalProductsEnabled", false);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(physicalProductId)
                        .quantity(1)
                        .build()))
                .shippingAddress("123 Tech Park, Silicon Valley, CA")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "buyer@bytevault.com", "John Doe", request));

        assertTrue(ex.getMessage().contains("Physical product orders are not supported"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Customer cannot view another user's order -> Throws AccessDeniedException")
    void testGetOrderById_CrossUserOwnership_ThrowsAccessDeniedException() {
        UUID ownerUserId = UUID.randomUUID();
        UUID attackerUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .userId(ownerUserId)
                .orderNumber("ORD-123")
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                orderService.getOrderById(orderId, attackerUserId, false));
    }

    @Test
    @DisplayName("Admin can view any user's order")
    void testGetOrderById_Admin_IsAllowed() {
        UUID ownerUserId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .userId(ownerUserId)
                .orderNumber("ORD-123")
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        OrderResponse response = orderService.getOrderById(orderId, adminUserId, true);

        assertNotNull(response);
        assertEquals(orderId, response.getId());
    }

    @Test
    @DisplayName("Admin can retrieve all orders in system")
    void testGetAllOrdersForAdmin() {
        Order o1 = Order.builder().id(UUID.randomUUID()).orderNumber("ORD-1").status(OrderStatus.PAID).orderType(OrderType.DIGITAL).totalAmount(BigDecimal.ONE).build();
        Order o2 = Order.builder().id(UUID.randomUUID()).orderNumber("ORD-2").status(OrderStatus.PENDING).orderType(OrderType.PHYSICAL).totalAmount(BigDecimal.TEN).build();

        when(orderRepository.findAll()).thenReturn(List.of(o1, o2));

        List<OrderResponse> all = orderService.getAllOrdersForAdmin();

        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Valid status transition succeeds: PENDING -> PAID")
    void testUpdateOrderStatus_ValidTransition() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-100")
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse updated = orderService.updateOrderStatus(orderId, OrderStatus.PAID);

        assertNotNull(updated);
        assertEquals(OrderStatus.PAID.name(), updated.getStatus());
    }

    @Test
    @DisplayName("Illegal status transition throws BadRequestException: CANCELLED -> PAID")
    void testUpdateOrderStatus_IllegalTransition_ThrowsBadRequestException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-100")
                .status(OrderStatus.CANCELLED)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                orderService.updateOrderStatus(orderId, OrderStatus.PAID));
    }

    @Test
    @DisplayName("Physical order without shipping address throws BadRequestException")
    void testCreatePhysicalOrder_MissingShippingAddress_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID physicalProductId = UUID.randomUUID();

        ProductSummaryResponse physicalProduct = ProductSummaryResponse.builder()
                .id(physicalProductId)
                .name("Mechanical Keyboard")
                .price(BigDecimal.valueOf(129.00))
                .productType("PHYSICAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(physicalProductId)).thenReturn(physicalProduct);
        org.springframework.test.util.ReflectionTestUtils.setField(orderService, "physicalProductsEnabled", true);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder()
                        .productId(physicalProductId)
                        .quantity(1)
                        .build()))
                .shippingAddress("   ") // Blank address
                .build();

        assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "buyer@bytevault.com", "Buyer", request));
    }

    @Test
    @DisplayName("Customer successfully cancels own pending order and dispatches event")
    void testCancelOrder_Success() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-200")
                .userId(userId)
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.valueOf(50.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse cancelled = orderService.cancelOrder(orderId, userId, false, "Accidental duplicate");

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED.name(), cancelled.getStatus());
    }

    @Test
    @DisplayName("Customer cannot cancel another user's order -> Throws AccessDeniedException")
    void testCancelOrder_CrossUser_ThrowsAccessDeniedException() {
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-200")
                .userId(ownerId)
                .status(OrderStatus.PENDING)
                .orderType(OrderType.DIGITAL)
                .totalAmount(BigDecimal.valueOf(50.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                orderService.cancelOrder(orderId, attackerId, false, "Malicious cancellation"));
    }

    @Test
    @DisplayName("Cannot cancel an order that is already SHIPPED")
    void testCancelOrder_AlreadyShipped_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-200")
                .userId(userId)
                .status(OrderStatus.SHIPPED)
                .orderType(OrderType.PHYSICAL)
                .totalAmount(BigDecimal.valueOf(50.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        assertThrows(BadRequestException.class, () ->
                orderService.cancelOrder(orderId, userId, false, "Too late"));
    }

    @Test
    @DisplayName("Digital order quantity is strictly capped to 1 unit in order snapshot")
    void testCreateOrder_DigitalQuantityCappedToOne() {
        UUID userId = UUID.randomUUID();
        UUID digitalProductId = UUID.randomUUID();

        ProductSummaryResponse digitalProduct = ProductSummaryResponse.builder()
                .id(digitalProductId)
                .name("High-Performance Java E-Book")
                .price(BigDecimal.valueOf(199.00))
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
                        .quantity(5) // Attempt to buy 5 licenses
                        .build()))
                .customerEmail("dev@bytevault.com")
                .customerName("Dev Buyer")
                .build();

        OrderResponse response = orderService.createOrder(userId, "dev@bytevault.com", "Dev Buyer", request);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getItems().get(0).getQuantity()); // Capped to 1
        assertEquals(BigDecimal.valueOf(199.00), response.getTotalAmount());
    }

    @Test
    @DisplayName("Order creation with zero or negative quantity throws BadRequestException")
    void testCreateOrder_InvalidQuantity_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CreateOrderRequest zeroQtyReq = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(productId).quantity(0).build()))
                .build();

        assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "buyer@bytevault.com", "Buyer", zeroQtyReq));

        CreateOrderRequest negativeQtyReq = CreateOrderRequest.builder()
                .items(List.of(OrderItemRequest.builder().productId(productId).quantity(-2).build()))
                .build();

        assertThrows(BadRequestException.class, () ->
                orderService.createOrder(userId, "buyer@bytevault.com", "Buyer", negativeQtyReq));
    }

    @Test
    @DisplayName("applyRefundCancel moves order to CANCELLED and invokes Fulfillment revoke")
    void testApplyRefundCancel_Success_TriggersRevocation() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PAID)
                .orderType(OrderType.DIGITAL)
                .orderNumber("ORD-REFUND-101")
                .totalAmount(BigDecimal.valueOf(199.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.applyRefundCancel(orderId);

        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, times(1)).save(order);
        verify(fulfillmentRevokeClient, times(1)).revokeEntitlementsByOrder(orderId);
    }

    @Test
    @DisplayName("applyRefundCancel on already-cancelled order is idempotent and skips save")
    void testApplyRefundCancel_AlreadyCancelled_Idempotent() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.CANCELLED)
                .orderType(OrderType.DIGITAL)
                .orderNumber("ORD-REFUND-102")
                .totalAmount(BigDecimal.valueOf(199.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        OrderResponse response = orderService.applyRefundCancel(orderId);

        assertNotNull(response);
        assertEquals("CANCELLED", response.getStatus());
        verify(orderRepository, never()).save(any());
        verify(fulfillmentRevokeClient, never()).revokeEntitlementsByOrder(any());
    }

    @Test
    @DisplayName("applyRefundCancel still cancels order even if fulfillment revoke throws exception")
    void testApplyRefundCancel_FulfillmentRevokeThrows_OrderStillCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PAID)
                .orderType(OrderType.DIGITAL)
                .orderNumber("ORD-REFUND-103")
                .totalAmount(BigDecimal.valueOf(299.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("Fulfillment connection timeout"))
                .when(fulfillmentRevokeClient).revokeEntitlementsByOrder(orderId);

        OrderResponse response = orderService.applyRefundCancel(orderId);

        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("applyRefundCancel on non-cancellable order status throws BadRequestException")
    void testApplyRefundCancel_InvalidStatus_ThrowsBadRequest() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.COMPLETED)
                .orderNumber("ORD-REFUND-104")
                .totalAmount(BigDecimal.valueOf(199.00))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(order));

        assertThrows(BadRequestException.class, () -> orderService.applyRefundCancel(orderId));
        verify(orderRepository, never()).save(any());
        verify(fulfillmentRevokeClient, never()).revokeEntitlementsByOrder(any());
    }
}
