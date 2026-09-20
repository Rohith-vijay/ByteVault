package com.bytevault.order.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.order.client.ProductClient;
import com.bytevault.order.dto.*;
import com.bytevault.order.entity.Order;
import com.bytevault.order.entity.OrderItem;
import com.bytevault.order.entity.OrderStatus;
import com.bytevault.order.entity.OrderType;
import com.bytevault.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final com.bytevault.order.repository.OrderItemRepository orderItemRepository;
    private final com.bytevault.order.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ProductClient productClient;
    private final com.bytevault.order.client.InventoryClient inventoryClient;
    private final RabbitTemplate rabbitTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bytevault.order.client.FulfillmentClient fulfillmentClient;

    public void setFulfillmentClient(com.bytevault.order.client.FulfillmentClient fulfillmentClient) {
        this.fulfillmentClient = fulfillmentClient;
    }

    @org.springframework.beans.factory.annotation.Value("${app.gateway.secret:platform_default_gateway_shared_secret}")
    private String gatewaySecret;

    @Transactional
    public OrderResponse createOrder(UUID userId, String customerEmail, String customerName, CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order items cannot be empty");
        }

        UUID orderId = UUID.randomUUID();
        String orderNumber = "ORD-" + System.currentTimeMillis() + "-" + orderId.toString().substring(0, 4).toUpperCase();
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean hasPhysical = false;

        List<OrderItem> orderItems = new ArrayList<>();
        List<OrderItemRequest> reservedPhysicalItems = new ArrayList<>();

        try {
            for (OrderItemRequest itemReq : request.getItems()) {
                if (itemReq.getProductId() == null) {
                    throw new BadRequestException("Product ID is required for each order item.");
                }
                if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                    throw new BadRequestException("Order item quantity must be at least 1.");
                }

                ProductSummaryResponse product;
                try {
                    product = productClient.getProductById(itemReq.getProductId());
                } catch (Exception e) {
                    log.error("[OrderService] Error validating product with product-service for productId={}: {}", itemReq.getProductId(), e.getMessage());
                    throw new BadRequestException("Failed to validate product with id: " + itemReq.getProductId());
                }

                if (product == null) {
                    throw new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId());
                }

                boolean isPurchasable = "PUBLISHED".equalsIgnoreCase(product.getStatus()) || "ACTIVE".equalsIgnoreCase(product.getStatus());
                if (product.getStatus() != null && !isPurchasable) {
                    throw new BadRequestException("Product '" + product.getName() + "' is inactive and unavailable for purchase.");
                }

                String productName = product.getName() != null ? product.getName() : "ByteVault Product";
                BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                boolean isDigital = !"PHYSICAL".equalsIgnoreCase(product.getProductType());

                int quantity = itemReq.getQuantity();
                if (isDigital && quantity > 1) {
                    log.info("[OrderService] Capping digital product quantity to 1 for productId={}", itemReq.getProductId());
                    quantity = 1;
                }

                if (!isDigital) {
                    hasPhysical = true;
                    if (inventoryClient != null) {
                        try {
                            com.bytevault.common.api.ApiResponse<Map<String, Object>> invRes =
                                    inventoryClient.reserveStock(itemReq.getProductId(), quantity, orderId);
                            if (invRes != null && !invRes.success()) {
                                throw new BadRequestException("Insufficient inventory available for product: " + productName);
                            }
                            reservedPhysicalItems.add(itemReq);
                        } catch (BadRequestException e) {
                            throw e;
                        } catch (Exception e) {
                            log.warn("[OrderService] Could not contact inventory-service via Feign: {}", e.getMessage());
                        }
                    }
                }

                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                totalAmount = totalAmount.add(subtotal);

                OrderItem orderItem = OrderItem.builder()
                        .productId(itemReq.getProductId())
                        .vendorId(product.getVendorId())
                        .productName(productName)
                        .sku(product.getSku())
                        .productType(product.getProductType() != null ? product.getProductType() : (isDigital ? "DIGITAL" : "PHYSICAL"))
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .subtotal(subtotal)
                        .isDigital(isDigital)
                        .build();

                orderItems.add(orderItem);
            }

            if (hasPhysical && (request.getShippingAddress() == null || request.getShippingAddress().trim().isEmpty())) {
                throw new BadRequestException("Shipping address is required for physical product orders.");
            }

            OrderType orderType = hasPhysical ? OrderType.PHYSICAL : OrderType.DIGITAL;

            Order order = Order.builder()
                    .id(orderId)
                    .orderNumber(orderNumber)
                    .userId(userId)
                    .status(OrderStatus.PENDING)
                    .orderType(orderType)
                    .totalAmount(totalAmount)
                    .customerEmail(customerEmail)
                    .customerName(customerName)
                    .shippingAddress(request.getShippingAddress())
                    .build();

            for (OrderItem item : orderItems) {
                order.addItem(item);
            }

            Order savedOrder = orderRepository.save(order);
            log.info("[OrderService] Created order: id={}, orderNumber={}, totalAmount={}, type={}",
                    savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getTotalAmount(), savedOrder.getOrderType());

            // Publish OrderCreatedEvent to RabbitMQ
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("orderId", savedOrder.getId().toString());
                event.put("orderNumber", savedOrder.getOrderNumber());
                event.put("userId", savedOrder.getUserId().toString());
                event.put("totalAmount", savedOrder.getTotalAmount().toString());
                event.put("orderType", savedOrder.getOrderType().name());
                event.put("customerEmail", savedOrder.getCustomerEmail());
                event.put("customerName", savedOrder.getCustomerName());

                List<String> productIds = savedOrder.getItems().stream()
                        .map(i -> i.getProductId().toString())
                        .collect(Collectors.toList());
                event.put("productIds", productIds);

                rabbitTemplate.convertAndSend("order.exchange", "order.created", event);
                log.info("[OrderService] Published OrderCreatedEvent: orderId={}", savedOrder.getId());
            } catch (Exception e) {
                log.warn("[OrderService] Could not publish OrderCreatedEvent: {}", e.getMessage());
            }

            return mapToResponse(savedOrder);
        } catch (Exception e) {
            if (!reservedPhysicalItems.isEmpty() && inventoryClient != null) {
                log.warn("[OrderService] Order creation failed for orderId={}. Executing inventory rollback...", orderId);
                try {
                    inventoryClient.releaseStock(orderId, null, 1);
                } catch (Exception releaseEx) {
                    log.error("[OrderService] Inventory rollback call failed: {}", releaseEx.getMessage());
                }
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<VendorSaleResponse> getVendorSales(UUID vendorId) {
        log.info("[OrderService] Fetching sales for vendor: {}", vendorId);
        List<OrderItem> items = orderItemRepository.findByVendorId(vendorId);
        return items.stream()
                .map(item -> {
                    Order order = item.getOrder();
                    return VendorSaleResponse.builder()
                            .orderItemId(item.getId())
                            .orderId(order != null ? order.getId() : null)
                            .orderNumber(order != null ? order.getOrderNumber() : null)
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .sku(item.getSku())
                            .productType(item.getProductType())
                            .unitPrice(item.getUnitPrice())
                            .quantity(item.getQuantity())
                            .subtotal(item.getSubtotal() != null ? item.getSubtotal() : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .orderStatus(order != null ? order.getStatus().name() : null)
                            .customerEmail(order != null ? order.getCustomerEmail() : null)
                            .customerName(order != null ? order.getCustomerName() : null)
                            .createdAt(item.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId, UUID userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to view this order.");
        }

        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrdersForAdmin() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        // Validate state transitions
        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new BadRequestException("Illegal order status transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        log.info("[OrderService] Order status updated: orderId={}, oldStatus={}, newStatus={}", orderId, currentStatus, newStatus);
        return mapToResponse(updated);
    }

    @Transactional
    public OrderResponse markOrderPaid(UUID orderId, OrderPaymentConfirmationRequest request) {
        log.info("[OrderService] Processing internal markOrderPaid: orderId={}, paymentId={}, amount={}",
                orderId, request.getPaymentId(), request.getAmount());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // 1. Idempotency check: if order is already PAID, return current order details safely
        if (order.getStatus() == OrderStatus.PAID) {
            log.info("[OrderService] Order {} is already marked as PAID. Returning idempotent success.", orderId);
            return mapToResponse(order);
        }

        // 2. Validate state transitions: cancelled or refunded orders cannot be marked as PAID
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            throw new BadRequestException("Illegal order status transition: Cannot pay for " + order.getStatus() + " order " + orderId);
        }

        // 3. Amount validation against authoritative order amount
        if (request.getAmount() == null || order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            log.error("[OrderService] Payment amount mismatch for order {}: expected={}, received={}",
                    orderId, order.getTotalAmount(), request.getAmount());
            throw new BadRequestException(String.format("Payment amount mismatch for order %s: expected %s, received %s",
                    orderId, order.getTotalAmount(), request.getAmount()));
        }

        // 4. Update order status to PAID
        order.setStatus(OrderStatus.PAID);
        Order savedOrder = orderRepository.save(order);
        log.info("[OrderService] Authoritative status transition complete: orderId={} is now PAID", orderId);

        // 5. Atomic Transactional Outbox Event Insertion in the same local database transaction
        try {
            List<String> productIds = savedOrder.getItems().stream()
                    .map(item -> item.getProductId().toString())
                    .collect(Collectors.toList());

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("orderId", savedOrder.getId().toString());
            eventPayload.put("orderNumber", savedOrder.getOrderNumber());
            eventPayload.put("userId", savedOrder.getUserId().toString());
            eventPayload.put("customerEmail", savedOrder.getCustomerEmail());
            eventPayload.put("customerName", savedOrder.getCustomerName());
            eventPayload.put("productIds", productIds);
            eventPayload.put("totalAmount", savedOrder.getTotalAmount());
            eventPayload.put("paymentId", request.getPaymentId());
            eventPayload.put("razorpayOrderId", request.getRazorpayOrderId());
            eventPayload.put("status", "PAID");
            eventPayload.put("orderType", savedOrder.getOrderType() != null ? savedOrder.getOrderType().name() : "DIGITAL");

            String jsonPayload = objectMapper != null ? objectMapper.writeValueAsString(eventPayload) : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(eventPayload);

            // Avoid duplicate outbox rows if an event already exists for this order
            if (outboxEventRepository.findByAggregateIdAndEventType(orderId, "order.paid").isEmpty()) {
                com.bytevault.order.entity.OutboxEvent outboxEvent = com.bytevault.order.entity.OutboxEvent.builder()
                        .aggregateType("ORDER")
                        .aggregateId(orderId)
                        .eventType("order.paid")
                        .payload(jsonPayload)
                        .status(com.bytevault.order.entity.OutboxStatus.PENDING)
                        .retryCount(0)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();

                outboxEventRepository.save(outboxEvent);
                log.info("[OrderService] Transactional outbox event created atomically for orderId={}", orderId);
            }

            // Direct synchronous fulfillment dispatch for immediate customer entitlement (NO RabbitMQ dependency)
            List<UUID> digitalProductIds = savedOrder.getItems().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getIsDigital()) || "DIGITAL".equalsIgnoreCase(item.getProductType()))
                    .map(com.bytevault.order.entity.OrderItem::getProductId)
                    .collect(Collectors.toList());
            if (!digitalProductIds.isEmpty()) {
                if (fulfillmentClient == null) {
                    log.error("[OrderService] CRITICAL: fulfillmentClient is null. Digital fulfillment cannot proceed for order {}", savedOrder.getId());
                    throw new IllegalStateException("Fulfillment service client unavailable for digital order " + savedOrder.getId());
                }
                log.info("[OrderService] Triggering synchronous digital fulfillment for order {} ({} digital items)", savedOrder.getId(), digitalProductIds.size());
                try {
                    fulfillmentClient.fulfillOrderInternal(gatewaySecret, "ROLE_INTERNAL_SERVICE", savedOrder.getId(), savedOrder.getUserId(), digitalProductIds);
                    log.info("[OrderService] Immediate synchronous digital fulfillment succeeded for order {}", savedOrder.getId());
                } catch (Exception ex) {
                    log.error("[OrderService] CRITICAL: Synchronous digital fulfillment failed for order {}: {}", savedOrder.getId(), ex.getMessage(), ex);
                    throw new RuntimeException("Digital product fulfillment failed for order " + savedOrder.getId() + ": " + ex.getMessage(), ex);
                }
            }
        } catch (Exception e) {
            log.error("[OrderService] Failed to persist outbox event for order {}. Rolling back transaction.", orderId, e);
            throw new RuntimeException("Failed to persist order payment event to outbox", e);
        }

        return mapToResponse(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId, UUID userId, boolean isAdmin, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to cancel this order.");
        }

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED) {
            return mapToResponse(order);
        }

        boolean isCancellable = currentStatus == OrderStatus.PENDING
                || currentStatus == OrderStatus.PAYMENT_PENDING
                || currentStatus == OrderStatus.PAID
                || currentStatus == OrderStatus.PROCESSING
                || currentStatus == OrderStatus.READY_FOR_FULFILLMENT;

        if (!isCancellable) {
            throw new BadRequestException("Cannot cancel order in status: " + currentStatus);
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);
        log.info("[OrderService] Order cancelled: orderId={}, cancelledBy={}, reason={}", orderId, userId, reason);

        // Publish OrderCancelledEvent to RabbitMQ
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("orderId", updated.getId().toString());
            event.put("orderNumber", updated.getOrderNumber());
            event.put("userId", updated.getUserId().toString());
            event.put("reason", reason != null ? reason : "Cancelled by user");
            rabbitTemplate.convertAndSend("order.exchange", "order.cancelled", event);
            log.info("[OrderService] Published OrderCancelledEvent: orderId={}", updated.getId());
        } catch (Exception e) {
            log.warn("[OrderService] Could not publish OrderCancelledEvent: {}", e.getMessage());
        }

        return mapToResponse(updated);
    }

    private boolean isValidStatusTransition(OrderStatus from, OrderStatus to) {
        if (from == to) return true;
        return switch (from) {
            case PENDING -> to == OrderStatus.PAYMENT_PENDING || to == OrderStatus.PAID || to == OrderStatus.CANCELLED || to == OrderStatus.FAILED;
            case PAYMENT_PENDING -> to == OrderStatus.PAID || to == OrderStatus.CANCELLED || to == OrderStatus.FAILED;
            case PAID -> to == OrderStatus.PROCESSING || to == OrderStatus.READY_FOR_FULFILLMENT || to == OrderStatus.FULFILLED || to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED || to == OrderStatus.REFUNDED;
            case PROCESSING -> to == OrderStatus.READY_FOR_FULFILLMENT || to == OrderStatus.FULFILLED || to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED || to == OrderStatus.REFUNDED;
            case READY_FOR_FULFILLMENT -> to == OrderStatus.FULFILLED || to == OrderStatus.SHIPPED || to == OrderStatus.CANCELLED || to == OrderStatus.REFUNDED;
            case SHIPPED -> to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED;
            case DELIVERED -> to == OrderStatus.COMPLETED;
            case FULFILLED -> to == OrderStatus.COMPLETED || to == OrderStatus.REFUNDED;
            case COMPLETED, CANCELLED, REFUNDED, FAILED -> false;
        };
    }

    private OrderResponse mapToResponse(Order o) {
        List<OrderItemResponse> itemResponses = o.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .vendorId(item.getVendorId())
                        .productName(item.getProductName())
                        .sku(item.getSku())
                        .productType(item.getProductType())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .isDigital(item.getIsDigital())
                        .subtotal(item.getSubtotal() != null ? item.getSubtotal() : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .userId(o.getUserId())
                .status(o.getStatus().name())
                .orderType(o.getOrderType().name())
                .totalAmount(o.getTotalAmount())
                .customerEmail(o.getCustomerEmail())
                .customerName(o.getCustomerName())
                .shippingAddress(o.getShippingAddress())
                .createdAt(o.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
