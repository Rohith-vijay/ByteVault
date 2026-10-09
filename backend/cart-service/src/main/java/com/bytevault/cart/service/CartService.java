package com.bytevault.cart.service;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.bytevault.cart.entity.Cart;
import com.bytevault.cart.entity.CartItem;
import com.bytevault.cart.repository.CartItemRepository;
import com.bytevault.cart.repository.CartRepository;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing customer shopping carts with durable PostgreSQL persistence.
 *
 * Microservice Ownership & Responsibilities:
 * - Cart owns only user cart session state (items, quantities, and display snapshots).
 * - Product Service remains the single authoritative source for catalog metadata and checkout pricing.
 * - Snapshots (productName, unitPrice, isDigital) stored here are display-only and non-authoritative for checkout.
 * - Redundant derived totals are computed on-the-fly to guarantee a single source of truth.
 * - Concurrency races on (cart_id, product_id) are handled deterministically.
 */
@Slf4j
@Service
public class CartService {

    public static final int MAX_QUANTITY_PER_ITEM = 999;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;
    private final TransactionTemplate transactionTemplate;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductClient productClient,
                       PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productClient = productClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Retrieves the shopping cart for the specified user using a read-only transaction.
     */
    @Transactional(readOnly = true)
    public CartResponseDto getCart(String userId) {
        log.debug("[CartService] Loading cart for userId={}", userId);
        return cartRepository.findByUserId(userId)
                .map(this::toResponseDto)
                .orElseGet(this::emptyCartResponse);
    }

    /**
     * Adds an item to the shopping cart, synchronizing display snapshot price and status from Product Service.
     * Concurrency races on (cart_id, product_id) are caught via DataIntegrityViolationException
     * and deterministically merged into the existing item quantity.
     */
    public CartResponseDto addItem(String userId, CartItemDto item) {
        log.info("[CartService] Adding item to cart for userId={}, productId={}, qty={}",
                userId, item.getProductId(), item.getQuantity());

        validateAddItemRequest(item);
        ProductSummaryResponse product = fetchAndValidateProduct(item.getProductId());

        try {
            return transactionTemplate.execute(status -> executeAddItem(userId, item, product));
        } catch (DataIntegrityViolationException ex) {
            log.info("[CartService] Concurrent add-to-cart collision detected for userId={}, productId={}. Retrying merge deterministically...",
                    userId, item.getProductId());
            return transactionTemplate.execute(status -> executeAddItem(userId, item, product));
        }
    }

    /**
     * Internal state-changing execution of addItem within an isolated transaction.
     */
    private CartResponseDto executeAddItem(String userId, CartItemDto item, ProductSummaryResponse product) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.saveAndFlush(Cart.builder().userId(userId).build()));

        boolean isDigital = "DIGITAL".equalsIgnoreCase(product.getProductType());
        BigDecimal authoritativePrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        String authoritativeName = product.getName() != null ? product.getName() : "Unknown Product";

        // Check if item already exists in the cart
        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(item.getProductId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            if (isDigital) {
                existing.setQuantity(1); // Digital products strictly capped at 1
            } else {
                int newQty = existing.getQuantity() + item.getQuantity();
                if (newQty > MAX_QUANTITY_PER_ITEM) {
                    throw new BadRequestException("Total item quantity cannot exceed " + MAX_QUANTITY_PER_ITEM + " units.");
                }
                existing.setQuantity(newQty);
            }
            existing.setUnitPrice(authoritativePrice);
            existing.setProductName(authoritativeName);
            existing.setIsDigital(isDigital);
        } else {
            int initialQty = isDigital ? 1 : item.getQuantity();
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .productId(item.getProductId())
                    .productName(authoritativeName)
                    .unitPrice(authoritativePrice)
                    .quantity(initialQty)
                    .isDigital(isDigital)
                    .build();
            cart.addItem(newItem);
        }

        Cart saved = cartRepository.saveAndFlush(cart);
        return toResponseDto(saved);
    }

    /**
     * Updates the quantity of an existing cart item.
     */
    @Transactional
    public CartResponseDto updateItemQuantity(String userId, UUID productId, int quantity) {
        log.info("[CartService] Updating item quantity for userId={}, productId={}, newQty={}",
                userId, productId, quantity);

        if (quantity < 0) {
            throw new BadRequestException("Quantity cannot be negative.");
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("Quantity cannot exceed " + MAX_QUANTITY_PER_ITEM + " units.");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));

        if (quantity == 0) {
            cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        } else {
            CartItem existing = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found in cart for productId: " + productId));

            if (Boolean.TRUE.equals(existing.getIsDigital()) && quantity > 1) {
                throw new BadRequestException("Digital products cannot have a quantity greater than 1.");
            }
            existing.setQuantity(quantity);
        }

        Cart saved = cartRepository.saveAndFlush(cart);
        return toResponseDto(saved);
    }

    /**
     * Removes an individual item from the user's cart.
     */
    @Transactional
    public CartResponseDto removeItem(String userId, UUID productId) {
        log.info("[CartService] Removing item from cart for userId={}, productId={}", userId, productId);

        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isEmpty()) {
            return emptyCartResponse();
        }

        Cart cart = cartOpt.get();
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        Cart saved = cartRepository.saveAndFlush(cart);
        return toResponseDto(saved);
    }

    /**
     * Clears all items from the user's cart.
     */
    @Transactional
    public CartResponseDto clearCart(String userId) {
        log.info("[CartService] Clearing cart for userId={}", userId);

        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isPresent()) {
            Cart cart = cartOpt.get();
            cart.getItems().clear();
            Cart saved = cartRepository.saveAndFlush(cart);
            return toResponseDto(saved);
        }
        return emptyCartResponse();
    }

    /**
     * Bulk persists or resets cart state with authoritative product validations.
     */
    @Transactional
    public CartResponseDto saveCart(String userId, List<CartItemDto> items) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.saveAndFlush(Cart.builder().userId(userId).build()));

        cart.getItems().clear();

        if (items != null) {
            for (CartItemDto rawItem : items) {
                if (rawItem.getProductId() != null && rawItem.getQuantity() != null && rawItem.getQuantity() > 0) {
                    try {
                        ProductSummaryResponse product = productClient.getProductById(rawItem.getProductId());
                        if (product != null && isPurchasable(product.getStatus())) {
                            boolean isDigital = "DIGITAL".equalsIgnoreCase(product.getProductType());
                            int qty = isDigital ? 1 : Math.min(rawItem.getQuantity(), MAX_QUANTITY_PER_ITEM);
                            CartItem lineItem = CartItem.builder()
                                    .cart(cart)
                                    .productId(product.getId())
                                    .productName(product.getName() != null ? product.getName() : "Product")
                                    .unitPrice(product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO)
                                    .quantity(qty)
                                    .isDigital(isDigital)
                                    .build();
                            cart.addItem(lineItem);
                        }
                    } catch (Exception ex) {
                        log.warn("[CartService] Skipping invalid or unavailable item during bulk save: productId={}",
                                rawItem.getProductId(), ex);
                    }
                }
            }
        }

        Cart saved = cartRepository.saveAndFlush(cart);
        return toResponseDto(saved);
    }

    private void validateAddItemRequest(CartItemDto item) {
        if (item.getProductId() == null) {
            throw new BadRequestException("Product ID is required.");
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be at least 1.");
        }
        if (item.getQuantity() > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("Quantity cannot exceed " + MAX_QUANTITY_PER_ITEM + " units.");
        }
    }

    private ProductSummaryResponse fetchAndValidateProduct(UUID productId) {
        ProductSummaryResponse product;
        try {
            product = productClient.getProductById(productId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[CartService] Error querying Product Service via Feign: {}", e.getMessage());
            product = null;
        }

        if (product == null) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }

        if (!isPurchasable(product.getStatus())) {
            throw new BadRequestException("Cannot add non-published product to cart: " + product.getName()
                    + " (status: " + product.getStatus() + ")");
        }

        return product;
    }

    private boolean isPurchasable(String status) {
        return "PUBLISHED".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * Dynamically calculates derived totals (subtotal, totalAmount, totalItems) in memory.
     * Guarantees a single source of truth without persisting redundant derived values.
     */
    private CartResponseDto toResponseDto(Cart cart) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return emptyCartResponse();
        }

        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        List<CartItemDto> itemDtos = new ArrayList<>();

        for (CartItem item : cart.getItems()) {
            BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));

            total = total.add(lineTotal);
            count += qty;

            itemDtos.add(CartItemDto.builder()
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .unitPrice(price)
                    .quantity(qty)
                    .isDigital(item.getIsDigital())
                    .build());
        }

        return CartResponseDto.builder()
                .items(itemDtos)
                .totalAmount(total.setScale(2, RoundingMode.HALF_UP))
                .totalItems(count)
                .build();
    }

    private CartResponseDto emptyCartResponse() {
        return CartResponseDto.builder()
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalItems(0)
                .build();
    }
}
