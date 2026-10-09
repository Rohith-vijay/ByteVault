package com.bytevault.cart;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.bytevault.cart.entity.Cart;
import com.bytevault.cart.entity.CartItem;
import com.bytevault.cart.repository.CartItemRepository;
import com.bytevault.cart.repository.CartRepository;
import com.bytevault.cart.service.CartService;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        // Setup transaction manager mock so TransactionTemplate executes smoothly
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        cartService = new CartService(cartRepository, cartItemRepository, productClient, transactionManager);
    }

    @Test
    @DisplayName("Add item to cart with PUBLISHED product validation and authoritative snapshot sync")
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

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemDto inputDto = CartItemDto.builder()
                .productId(productId)
                .quantity(2)
                .build();

        CartResponseDto result = cartService.addItem(userId, inputDto);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(2, result.getTotalItems());
        assertEquals(BigDecimal.valueOf(1198.00).setScale(2), result.getTotalAmount());

        CartItemDto savedItem = result.getItems().get(0);
        assertEquals(productId, savedItem.getProductId());
        assertEquals("Spring in Action", savedItem.getProductName());
        assertEquals(BigDecimal.valueOf(599.00), savedItem.getUnitPrice());
        assertEquals(2, savedItem.getQuantity());
        assertFalse(savedItem.getIsDigital());

        verify(cartRepository, times(1)).saveAndFlush(any(Cart.class));
    }

    @Test
    @DisplayName("Reject adding DRAFT product with BadRequestException")
    void testAddItemToCart_DraftProductRejection() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse draftProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Unpublished E-Book")
                .price(BigDecimal.valueOf(199.00))
                .productType("DIGITAL")
                .status("DRAFT")
                .build();

        when(productClient.getProductById(productId)).thenReturn(draftProduct);

        CartItemDto inputDto = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, inputDto));
        verify(cartRepository, never()).saveAndFlush(any(Cart.class));
    }

    @Test
    @DisplayName("Reject adding INACTIVE product with BadRequestException")
    void testAddItemToCart_InactiveProductRejection() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse inactiveProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Deprecated Library")
                .price(BigDecimal.valueOf(99.00))
                .productType("DIGITAL")
                .status("INACTIVE")
                .build();

        when(productClient.getProductById(productId)).thenReturn(inactiveProduct);

        CartItemDto inputDto = CartItemDto.builder()
                .productId(productId)
                .quantity(1)
                .build();

        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, inputDto));
        verify(cartRepository, never()).saveAndFlush(any(Cart.class));
    }

    @Test
    @DisplayName("Enforce single quantity cap for DIGITAL products")
    void testAddItemToCart_DigitalProductQuantityCapping() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse digitalProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Clean Architecture Video Course")
                .price(BigDecimal.valueOf(1299.00))
                .productType("DIGITAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(digitalProduct);

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemDto inputDto = CartItemDto.builder()
                .productId(productId)
                .quantity(5) // Requesting 5 units of a digital product
                .build();

        CartResponseDto result = cartService.addItem(userId, inputDto);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getItems().get(0).getQuantity());
        assertTrue(result.getItems().get(0).getIsDigital());
        assertEquals(BigDecimal.valueOf(1299.00).setScale(2), result.getTotalAmount());
    }

    @Test
    @DisplayName("Increment quantity for existing physical item")
    void testAddItemToCart_ExistingPhysicalItemIncrement() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        ProductSummaryResponse product = ProductSummaryResponse.builder()
                .id(productId)
                .name("Microservices Patterns")
                .price(BigDecimal.valueOf(450.00))
                .productType("PHYSICAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(product);

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        CartItem existingItem = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productName("Microservices Patterns")
                .unitPrice(BigDecimal.valueOf(450.00))
                .quantity(2)
                .isDigital(false)
                .build();
        cart.addItem(existingItem);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemDto inputDto = CartItemDto.builder()
                .productId(productId)
                .quantity(3)
                .build();

        CartResponseDto result = cartService.addItem(userId, inputDto);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getTotalItems());
        assertEquals(5, result.getItems().get(0).getQuantity());
        assertEquals(BigDecimal.valueOf(2250.00).setScale(2), result.getTotalAmount());
    }

    @Test
    @DisplayName("Reject adding items exceeding maximum quantity limit")
    void testAddItemToCart_ExceedMaxQuantityPerItem() {
        UUID productId = UUID.randomUUID();
        String userId = "user-123";

        CartItemDto inputDto = CartItemDto.builder()
                .productId(productId)
                .quantity(CartService.MAX_QUANTITY_PER_ITEM + 1)
                .build();

        assertThrows(BadRequestException.class, () -> cartService.addItem(userId, inputDto));
        verify(productClient, never()).getProductById(any());
    }

    @Test
    @DisplayName("Retrieve cart with dynamically calculated totals")
    void testGetCart_Existing() {
        String userId = "user-123";
        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        cart.addItem(CartItem.builder()
                .cart(cart)
                .productId(UUID.randomUUID())
                .productName("Design Patterns")
                .unitPrice(BigDecimal.valueOf(100.00))
                .quantity(2)
                .isDigital(false)
                .build());
        cart.addItem(CartItem.builder()
                .cart(cart)
                .productId(UUID.randomUUID())
                .productName("Algorithms Guide")
                .unitPrice(BigDecimal.valueOf(50.00))
                .quantity(1)
                .isDigital(true)
                .build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        CartResponseDto result = cartService.getCart(userId);

        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertEquals(3, result.getTotalItems());
        assertEquals(BigDecimal.valueOf(250.00).setScale(2), result.getTotalAmount());
    }

    @Test
    @DisplayName("Retrieve cart when user has no cart returns empty response")
    void testGetCart_NotFound() {
        String userId = "unknown-user";
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        CartResponseDto result = cartService.getCart(userId);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalAmount());
    }

    @Test
    @DisplayName("Update item quantity successfully")
    void testUpdateItemQuantity_Success() {
        String userId = "user-123";
        UUID productId = UUID.randomUUID();

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productName("Effective Java")
                .unitPrice(BigDecimal.valueOf(200.00))
                .quantity(1)
                .isDigital(false)
                .build();
        cart.addItem(item);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDto result = cartService.updateItemQuantity(userId, productId, 4);

        assertNotNull(result);
        assertEquals(4, result.getTotalItems());
        assertEquals(BigDecimal.valueOf(800.00).setScale(2), result.getTotalAmount());
    }

    @Test
    @DisplayName("Update item quantity to zero removes item from cart")
    void testUpdateItemQuantity_ZeroRemovesItem() {
        String userId = "user-123";
        UUID productId = UUID.randomUUID();

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productName("Effective Java")
                .unitPrice(BigDecimal.valueOf(200.00))
                .quantity(1)
                .isDigital(false)
                .build();
        cart.addItem(item);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDto result = cartService.updateItemQuantity(userId, productId, 0);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getTotalAmount());
    }

    @Test
    @DisplayName("Update digital item quantity greater than 1 throws BadRequestException")
    void testUpdateItemQuantity_DigitalProductExceedsOne() {
        String userId = "user-123";
        UUID productId = UUID.randomUUID();

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productName("Cloud DevOps PDF")
                .unitPrice(BigDecimal.valueOf(150.00))
                .quantity(1)
                .isDigital(true)
                .build();
        cart.addItem(item);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        assertThrows(BadRequestException.class, () -> cartService.updateItemQuantity(userId, productId, 2));
    }

    @Test
    @DisplayName("Remove item from cart")
    void testRemoveItem() {
        String userId = "user-123";
        UUID productId = UUID.randomUUID();

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        CartItem item = CartItem.builder()
                .cart(cart)
                .productId(productId)
                .productName("Java Concurrency")
                .unitPrice(BigDecimal.valueOf(300.00))
                .quantity(1)
                .isDigital(false)
                .build();
        cart.addItem(item);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDto result = cartService.removeItem(userId, productId);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
    }

    @Test
    @DisplayName("Clear entire cart")
    void testClearCart() {
        String userId = "user-123";
        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        cart.addItem(CartItem.builder()
                .cart(cart)
                .productId(UUID.randomUUID())
                .productName("Item A")
                .unitPrice(BigDecimal.valueOf(50.00))
                .quantity(1)
                .isDigital(false)
                .build());

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponseDto result = cartService.clearCart(userId);

        assertNotNull(result);
        assertEquals(0, result.getItems().size());
        assertEquals(0, result.getTotalItems());
    }
}
