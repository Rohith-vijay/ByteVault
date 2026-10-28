package com.bytevault.cart.integration;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.config.FeignClientConfig;
import com.bytevault.cart.controller.CartController;
import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.bytevault.cart.entity.Cart;
import com.bytevault.cart.entity.CartItem;
import com.bytevault.cart.repository.CartItemRepository;
import com.bytevault.cart.repository.CartRepository;
import com.bytevault.cart.service.CartService;
import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.exception.BadRequestException;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration and security isolation tests preserving user data boundaries (anti-IDOR),
 * deterministic concurrency recovery, and non-fabrication of downstream security identities.
 */
@ExtendWith(MockitoExtension.class)
public class CartUserIsolationTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CartService cartService;
    private CartController cartController;

    private String userA;
    private String userB;

    @BeforeEach
    void setUp() {
        userA = "user-alpha-" + UUID.randomUUID();
        userB = "user-beta-" + UUID.randomUUID();

        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        cartService = new CartService(cartRepository, cartItemRepository, productClient, transactionManager);
        cartController = new CartController(cartService);
    }

    @Test
    @DisplayName("User cart data isolation (anti-IDOR): User A items do not leak to User B cart")
    void testUserCartDataIsolation() {
        UUID productA = UUID.randomUUID();
        Cart cartA = Cart.builder().userId(userA).items(new ArrayList<>()).build();
        cartA.addItem(CartItem.builder()
                .cart(cartA)
                .productId(productA)
                .productName("Cloud DevOps Guide")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(129.99))
                .isDigital(true)
                .build());

        Cart cartB = Cart.builder().userId(userB).items(new ArrayList<>()).build();

        when(cartRepository.findByUserId(userA)).thenReturn(Optional.of(cartA));
        when(cartRepository.findByUserId(userB)).thenReturn(Optional.of(cartB));

        ResponseEntity<ApiResponse<CartResponseDto>> resA = cartController.getCart(userA, null);
        ResponseEntity<ApiResponse<CartResponseDto>> resB = cartController.getCart(userB, null);

        assertNotNull(resA.getBody());
        assertNotNull(resB.getBody());

        CartResponseDto bodyA = resA.getBody().data();
        CartResponseDto bodyB = resB.getBody().data();

        assertEquals(1, bodyA.getItems().size());
        assertEquals("Cloud DevOps Guide", bodyA.getItems().get(0).getProductName());
        assertEquals(2, bodyA.getTotalItems());

        // Verify User B cart is completely isolated and empty
        assertEquals(0, bodyB.getItems().size());
        assertEquals(0, bodyB.getTotalItems());
        assertEquals(BigDecimal.ZERO.setScale(2), bodyB.getTotalAmount());
    }

    @Test
    @DisplayName("Product synchronization: Adding published product captures authoritative price snapshot")
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

        Cart cart = Cart.builder().userId(userA).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(userA)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartItemDto req = CartItemDto.builder()
                .productId(productId)
                .quantity(3)
                .build();

        ResponseEntity<ApiResponse<CartResponseDto>> response = cartController.addItem(userA, req, null);

        assertNotNull(response.getBody());
        CartResponseDto resultCart = response.getBody().data();
        assertEquals(1, resultCart.getItems().size());
        assertEquals(BigDecimal.valueOf(85.00), resultCart.getItems().get(0).getUnitPrice());
        assertEquals(BigDecimal.valueOf(255.00).setScale(2), resultCart.getTotalAmount());
        assertEquals(3, resultCart.getTotalItems());
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
        verify(cartRepository, never()).saveAndFlush(any());
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
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deterministic concurrency handling: Unique constraint collision recovers and merges quantity")
    void testDeterministicConcurrencyHandlingOnAdd() {
        UUID productId = UUID.randomUUID();
        ProductSummaryResponse liveProduct = ProductSummaryResponse.builder()
                .id(productId)
                .name("Kubernetes Handbook")
                .price(BigDecimal.valueOf(100.00))
                .productType("PHYSICAL")
                .status("PUBLISHED")
                .build();

        when(productClient.getProductById(productId)).thenReturn(liveProduct);

        // First attempt simulates thread collision where cart had no item, but save triggers unique constraint collision
        Cart cartAttempt1 = Cart.builder().userId(userA).items(new ArrayList<>()).build();

        // Second attempt represents re-loaded state with the newly inserted item from the competing thread
        Cart cartAttempt2 = Cart.builder().userId(userA).items(new ArrayList<>()).build();
        cartAttempt2.addItem(CartItem.builder()
                .cart(cartAttempt2)
                .productId(productId)
                .productName("Kubernetes Handbook")
                .unitPrice(BigDecimal.valueOf(100.00))
                .quantity(2)
                .isDigital(false)
                .build());

        when(cartRepository.findByUserId(userA))
                .thenReturn(Optional.of(cartAttempt1))  // attempt 1
                .thenReturn(Optional.of(cartAttempt2)); // attempt 2 (retry)

        when(cartRepository.saveAndFlush(any(Cart.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key value violates unique constraint uk_cart_item_cart_product")) // attempt 1 fails
                .thenAnswer(invocation -> invocation.getArgument(0)); // attempt 2 succeeds

        CartItemDto req = CartItemDto.builder()
                .productId(productId)
                .quantity(3)
                .build();

        CartResponseDto result = cartService.addItem(userA, req);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        // 2 (existing from colliding thread) + 3 (our addition) = 5 total
        assertEquals(5, result.getTotalItems());
        assertEquals(BigDecimal.valueOf(500.00).setScale(2), result.getTotalAmount());
        verify(cartRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Downstream Feign security: Injects X-Gateway-Secret and never fabricates fake user ID")
    void testFeignSecurityInterceptorDoesNotFabricateUserId() {
        SecurityContextHolder.clearContext(); // Unauthenticated context

        FeignClientConfig config = new FeignClientConfig();
        ReflectionTestUtils.setField(config, "gatewaySecret", "test_shared_secret");

        RequestInterceptor interceptor = config.feignSecurityAndCorrelationInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        // 1. Must contain X-Gateway-Secret
        assertTrue(template.headers().containsKey("X-Gateway-Secret"));
        assertEquals("test_shared_secret", template.headers().get("X-Gateway-Secret").iterator().next());

        // 2. Must contain internal service role
        assertTrue(template.headers().containsKey("X-User-Roles"));
        assertEquals("ROLE_INTERNAL_SERVICE", template.headers().get("X-User-Roles").iterator().next());

        // 3. Must NEVER fabricate or inject a fake X-User-Id
        assertFalse(template.headers().containsKey("X-User-Id"));
    }
}
