package com.bytevault.cart;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.bytevault.cart.service.CartService;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private CartService cartService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Add item to cart with PUBLISHED product validation and authoritative price sync")
    void testAddItemToCart_PublishedProduct() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse product = ProductSummaryResponse.builder()
                .id(productId)
                .name("Spring in Action")
                .price(BigDecimal.valueOf(599.00))
                .productType("PHYSICAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(product);
        when(valueOperations.get("cart:" + userId)).thenReturn(new CartResponseDto());

        // Client passes arbitrary unverified price
        CartItemDto item = CartItemDto.builder()
                .productId(productId)
                .productName("Fake Name")
                .unitPrice(BigDecimal.valueOf(1.00))
                .quantity(2)
                .build();

        CartResponseDto cart = cartService.addItem(userId, item);

        assertNotNull(cart);
        assertEquals(1, cart.getItems().size());
        assertEquals("Spring in Action", cart.getItems().get(0).getProductName());
        assertEquals(BigDecimal.valueOf(599.00), cart.getItems().get(0).getUnitPrice());
        assertEquals(BigDecimal.valueOf(1198.00).setScale(2), cart.getTotalAmount());
        assertEquals(2, cart.getTotalItems());
        verify(valueOperations, times(1)).set(eq("cart:" + userId), any(CartResponseDto.class), eq(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("Digital product quantity is strictly capped to 1")
    void testAddItemToCart_DigitalProductQuantityCapped() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse digitalProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Cloud Architecture Guide")
                .price(BigDecimal.valueOf(149.99))
                .productType("DIGITAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(digitalProduct);
        when(valueOperations.get("cart:" + userId)).thenReturn(new CartResponseDto());

        CartItemDto item = CartItemDto.builder()
                .productId(productId)
                .quantity(5) // Attempt to add 5 digital licenses
                .build();

        CartResponseDto cart = cartService.addItem(userId, item);

        assertNotNull(cart);
        assertEquals(1, cart.getItems().size());
        assertEquals(1, cart.getItems().get(0).getQuantity()); // Capped to 1
        assertTrue(cart.getItems().get(0).getIsDigital());
        assertEquals(BigDecimal.valueOf(149.99), cart.getTotalAmount());
    }

    @Test
    @DisplayName("Add item with legacy ACTIVE status is accepted")
    void testAddItemToCart_ActiveProduct() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse product = ProductSummaryResponse.builder()
                .id(productId)
                .name("Legacy Active Item")
                .price(BigDecimal.valueOf(299.00))
                .productType("PHYSICAL")
                .status("ACTIVE")
                .build();

        when(productClient.getProductById(productId)).thenReturn(product);
        when(valueOperations.get("cart:" + userId)).thenReturn(new CartResponseDto());

        CartItemDto item = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        CartResponseDto cart = cartService.addItem(userId, item);

        assertNotNull(cart);
        assertEquals(1, cart.getItems().size());
        assertEquals("Legacy Active Item", cart.getItems().get(0).getProductName());
    }

    @Test
    @DisplayName("Add DRAFT product to cart -> Throws BadRequestException")
    void testAddDraftProduct_ThrowsBadRequestException() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse draftProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Draft Product")
                .price(BigDecimal.valueOf(100.00))
                .status("DRAFT")
                .build();

        when(productClient.getProductById(productId)).thenReturn(draftProduct);

        CartItemDto item = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> cartService.addItem(userId, item));
        assertTrue(ex.getMessage().contains("Cannot add non-published product"));
        verify(valueOperations, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("Add DEACTIVATED product to cart -> Throws BadRequestException")
    void testAddDeactivatedProduct_ThrowsBadRequestException() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse deactivated = ProductSummaryResponse.builder()
                .id(productId)
                .name("Deactivated Product")
                .price(BigDecimal.valueOf(100.00))
                .status("DEACTIVATED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(deactivated);

        CartItemDto item = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, item));
    }

    @Test
    @DisplayName("Add nonexistent product to cart -> Throws ResourceNotFoundException")
    void testAddNonexistentProduct_ThrowsResourceNotFoundException() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        when(productClient.getProductById(productId)).thenReturn(null);

        CartItemDto item = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> cartService.addItem(userId, item));
    }

    @Test
    @DisplayName("Invalid quantity (<= 0 or > 999) -> Throws BadRequestException")
    void testInvalidQuantityValidation() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        CartItemDto zeroQty = CartItemDto.builder().productId(productId).quantity(0).build();
        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, zeroQty));

        CartItemDto negativeQty = CartItemDto.builder().productId(productId).quantity(-1).build();
        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, negativeQty));

        CartItemDto excessiveQty = CartItemDto.builder().productId(productId).quantity(1000).build();
        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, excessiveQty));
    }

    @Test
    @DisplayName("Update item quantity in cart with price recalculation")
    void testUpdateItemQuantity() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        CartResponseDto existingCart = new CartResponseDto();
        existingCart.getItems().add(CartItemDto.builder()
                .productId(productId)
                .productName("Spring in Action")
                .unitPrice(BigDecimal.valueOf(500.00))
                .quantity(1)
                .isDigital(false)
                .build());

        when(valueOperations.get("cart:" + userId)).thenReturn(existingCart);

        CartResponseDto updatedCart = cartService.updateItemQuantity(userId, productId, 3);

        assertNotNull(updatedCart);
        assertEquals(1, updatedCart.getItems().size());
        assertEquals(3, updatedCart.getItems().get(0).getQuantity());
        assertEquals(BigDecimal.valueOf(1500.00).setScale(2), updatedCart.getTotalAmount());
        assertEquals(3, updatedCart.getTotalItems());
    }

    @Test
    @DisplayName("Updating digital product quantity > 1 throws BadRequestException")
    void testUpdateDigitalItemQuantity_ThrowsBadRequestException() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        CartResponseDto existingCart = new CartResponseDto();
        existingCart.getItems().add(CartItemDto.builder()
                .productId(productId)
                .productName("Digital Audio Track")
                .unitPrice(BigDecimal.valueOf(25.00))
                .quantity(1)
                .isDigital(true)
                .build());

        when(valueOperations.get("cart:" + userId)).thenReturn(existingCart);

        assertThrows(BadRequestException.class, () -> cartService.updateItemQuantity(userId, productId, 2));
    }

    @Test
    @DisplayName("Remove item from cart")
    void testRemoveItemFromCart() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        CartResponseDto existingCart = new CartResponseDto();
        existingCart.getItems().add(CartItemDto.builder().productId(productId).quantity(1).unitPrice(BigDecimal.TEN).build());

        when(valueOperations.get("cart:" + userId)).thenReturn(existingCart);

        CartResponseDto cart = cartService.removeItem(userId, productId);

        assertNotNull(cart);
        assertEquals(0, cart.getItems().size());
        assertEquals(BigDecimal.ZERO.setScale(2), cart.getTotalAmount());
        assertEquals(0, cart.getTotalItems());
    }

    @Test
    @DisplayName("Clear cart resets cart state in Redis")
    void testClearCart() {
        String userId = "user-123";

        CartResponseDto cleared = cartService.clearCart(userId);

        assertNotNull(cleared);
        assertTrue(cleared.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO.setScale(2), cleared.getTotalAmount());
        verify(valueOperations, times(1)).set(eq("cart:" + userId), any(CartResponseDto.class), eq(Duration.ofDays(7)));
    }
}
