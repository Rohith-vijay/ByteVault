package com.bytevault.cart.integration;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.controller.CartController;
import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.bytevault.cart.service.CartService;
import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartRedisIsolationIntegrationTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ProductClient productClient;

    private CartService cartService;
    private CartController cartController;

    private String userA;
    private String userB;

    @BeforeEach
    void setUp() {
        userA = "user-alpha-" + UUID.randomUUID();
        userB = "user-beta-" + UUID.randomUUID();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cartService = new CartService(redisTemplate, productClient);
        cartController = new CartController(cartService);
    }

    @Test
    @DisplayName("Redis namespace isolation: User A items do not leak to User B cart")
    void testRedisUserIsolation() {
        CartResponseDto cartA = new CartResponseDto();
        cartA.getItems().add(CartItemDto.builder()
                .productId(UUID.randomUUID())
                .productName("Cloud DevOps Guide")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(129.99))
                .build());

        when(valueOperations.get("cart:" + userA)).thenReturn(cartA);
        when(valueOperations.get("cart:" + userB)).thenReturn(new CartResponseDto());

        ResponseEntity<ApiResponse<CartResponseDto>> resA = cartController.getCart(userA, null);
        ResponseEntity<ApiResponse<CartResponseDto>> resB = cartController.getCart(userB, null);

        assertNotNull(resA.getBody());
        assertNotNull(resB.getBody());
        assertEquals(1, resA.getBody().data().getItems().size());
        assertEquals("Cloud DevOps Guide", resA.getBody().data().getItems().get(0).getProductName());
        assertEquals(0, resB.getBody().data().getItems().size());
    }

    @Test
    @DisplayName("Product synchronization: Adding published product syncs authoritative catalog price")
    void testPublishedProductPriceSync() {
        UUID productId = UUID.randomUUID();
        ProductSummaryResponse liveProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Microservices in Go")
                .price(BigDecimal.valueOf(85.00))
                .productType("PHYSICAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(liveProduct);
        when(valueOperations.get("cart:" + userA)).thenReturn(new CartResponseDto());

        CartItemDto req = CartItemDto.builder()
                .productId(productId)
                .quantity(3)
                .build();

        ResponseEntity<ApiResponse<CartResponseDto>> response = cartController.addItem(userA, req, null);

        assertNotNull(response.getBody());
        CartResponseDto cart = response.getBody().data();
        assertEquals(1, cart.getItems().size());
        assertEquals(BigDecimal.valueOf(85.00), cart.getItems().get(0).getUnitPrice());
        assertEquals(BigDecimal.valueOf(255.00).setScale(2), cart.getTotalAmount());
        verify(valueOperations, times(1)).set(eq("cart:" + userA), any(CartResponseDto.class), eq(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("Draft product rejection: Adding draft item throws BadRequestException")
    void testDraftProductRejection() {
        UUID productId = UUID.randomUUID();
        ProductSummaryResponse draftProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Unpublished Draft Course")
                .price(BigDecimal.valueOf(50.00))
                .status("DRAFT")
                .build();

        when(productClient.getProductById(productId)).thenReturn(draftProduct);

        CartItemDto req = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        assertThrows(BadRequestException.class, () -> cartController.addItem(userA, req, null));
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("Inactive product rejection: Adding disabled item throws BadRequestException")
    void testInactiveProductRejection() {
        UUID productId = UUID.randomUUID();
        ProductSummaryResponse inactiveProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Deprecated Library")
                .price(BigDecimal.valueOf(10.00))
                .status("INACTIVE")
                .build();

        when(productClient.getProductById(productId)).thenReturn(inactiveProduct);

        CartItemDto req = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        assertThrows(BadRequestException.class, () -> cartController.addItem(userA, req, null));
        verify(valueOperations, never()).set(any(), any(), any());
    }
}
