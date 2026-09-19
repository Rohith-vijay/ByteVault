package com.bytevault.cart.service;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing user-scoped shopping cart operations,
 * Redis persistence with TTL, quantity validation, and authoritative product price synchronization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final Duration CART_TTL = Duration.ofDays(7);
    private static final int MAX_QUANTITY_PER_ITEM = 999;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductClient productClient;

    /**
     * Retrieves the shopping cart for the specified user.
     */
    public CartResponseDto getCart(String userId) {
        log.debug("[CartService] Loading cart for userId={}", userId);
        return loadCart(userId);
    }

    /**
     * Adds an item to the shopping cart, synchronizing authoritative price and status from product-service.
     */
    public CartResponseDto addItem(String userId, CartItemDto item) {
        log.info("[CartService] Adding item to cart for userId={}, productId={}, qty={}",
                userId, item.getProductId(), item.getQuantity());

        if (item.getProductId() == null) {
            throw new BadRequestException("Product ID is required.");
        }

        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be at least 1.");
        }

        if (item.getQuantity() > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("Quantity cannot exceed " + MAX_QUANTITY_PER_ITEM + " units.");
        }

        // 1. Authoritative product validation from product-service
        ProductSummaryResponse product = null;
        try {
            product = productClient.getProductById(item.getProductId());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[CartService] Could not query product-service via Feign: {}", e.getMessage());
        }

        if (product == null) {
            throw new ResourceNotFoundException("Product not found with ID: " + item.getProductId());
        }

        // 2. Validate product status (Only PUBLISHED products are purchasable; ACTIVE accepted for legacy compat)
        boolean isPurchasable = "PUBLISHED".equalsIgnoreCase(product.getStatus()) || "ACTIVE".equalsIgnoreCase(product.getStatus());
        if (product.getStatus() != null && !isPurchasable) {
            throw new BadRequestException("Cannot add non-published product to cart: " + product.getName() + " (status: " + product.getStatus() + ")");
        }

        // 3. Override client-submitted fields with authoritative data
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO);
        boolean isDigital = "DIGITAL".equalsIgnoreCase(product.getProductType());
        item.setIsDigital(isDigital);

        // Digital products are capped at 1 unit per customer
        if (isDigital && item.getQuantity() > 1) {
            log.info("[CartService] Capping digital product quantity to 1 for productId={}", item.getProductId());
            item.setQuantity(1);
        }

        // 4. Update cart state
        CartResponseDto cart = loadCart(userId);
        boolean found = false;

        for (CartItemDto existing : cart.getItems()) {
            if (existing.getProductId().equals(item.getProductId())) {
                if (isDigital) {
                    existing.setQuantity(1); // Retain 1 for digital goods
                } else {
                    int newQty = existing.getQuantity() + item.getQuantity();
                    if (newQty > MAX_QUANTITY_PER_ITEM) {
                        throw new BadRequestException("Total item quantity cannot exceed " + MAX_QUANTITY_PER_ITEM + " units.");
                    }
                    existing.setQuantity(newQty);
                }
                // Refresh unit price to latest authoritative price
                existing.setUnitPrice(item.getUnitPrice());
                existing.setProductName(item.getProductName());
                existing.setIsDigital(isDigital);
                found = true;
                break;
            }
        }

        if (!found) {
            cart.getItems().add(item);
        }

        saveCart(userId, cart);
        return cart;
    }

    /**
     * Updates the quantity of an existing cart item.
     */
    public CartResponseDto updateItemQuantity(String userId, UUID productId, int quantity) {
        log.info("[CartService] Updating item quantity for userId={}, productId={}, newQty={}",
                userId, productId, quantity);

        if (quantity < 0) {
            throw new BadRequestException("Quantity cannot be negative.");
        }

        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new BadRequestException("Quantity cannot exceed " + MAX_QUANTITY_PER_ITEM + " units.");
        }

        CartResponseDto cart = loadCart(userId);

        if (quantity == 0) {
            cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        } else {
            boolean found = false;
            for (CartItemDto existing : cart.getItems()) {
                if (existing.getProductId().equals(productId)) {
                    if (Boolean.TRUE.equals(existing.getIsDigital()) && quantity > 1) {
                        throw new BadRequestException("Digital products cannot have a quantity greater than 1.");
                    }
                    existing.setQuantity(quantity);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new ResourceNotFoundException("Item not found in cart for productId: " + productId);
            }
        }

        saveCart(userId, cart);
        return cart;
    }

    /**
     * Removes an item from the cart.
     */
    public CartResponseDto removeItem(String userId, UUID productId) {
        log.info("[CartService] Removing item from cart for userId={}, productId={}", userId, productId);
        CartResponseDto cart = loadCart(userId);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        saveCart(userId, cart);
        return cart;
    }

    /**
     * Clears the entire cart for the specified user.
     */
    public CartResponseDto clearCart(String userId) {
        log.info("[CartService] Clearing cart for userId={}", userId);
        CartResponseDto emptyCart = new CartResponseDto();
        saveCart(userId, emptyCart);
        return emptyCart;
    }

    /**
     * Synchronizes and saves full cart state with authoritative price validations.
     */
    public CartResponseDto saveCart(String userId, List<CartItemDto> items) {
        CartResponseDto cart = new CartResponseDto();
        if (items != null) {
            for (CartItemDto rawItem : items) {
                if (rawItem.getProductId() != null) {
                    try {
                        addItem(userId, rawItem);
                    } catch (Exception e) {
                        log.warn("[CartService] Skipping invalid item during bulk save: {}", e.getMessage());
                    }
                }
            }
            cart = loadCart(userId);
        } else {
            saveCart(userId, cart);
        }
        return cart;
    }

    private CartResponseDto loadCart(String userId) {
        String key = "cart:" + userId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof CartResponseDto) {
            CartResponseDto cart = (CartResponseDto) obj;
            recalculateTotals(cart);
            return cart;
        }
        return new CartResponseDto();
    }

    private void saveCart(String userId, CartResponseDto cart) {
        recalculateTotals(cart);
        String key = "cart:" + userId;
        redisTemplate.opsForValue().set(key, cart, CART_TTL);
    }

    private void recalculateTotals(CartResponseDto cart) {
        if (cart.getItems() == null) {
            cart.setItems(new ArrayList<>());
        }

        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        for (CartItemDto item : cart.getItems()) {
            if (item.getUnitPrice() != null && item.getQuantity() != null) {
                BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                total = total.add(lineTotal);
                count += item.getQuantity();
            }
        }

        cart.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        cart.setTotalItems(count);
    }
}
