package com.bytevault.cart.controller;

import com.bytevault.cart.dto.CartItemDto;
import com.bytevault.cart.dto.CartResponseDto;
import com.bytevault.cart.service.CartService;
import com.bytevault.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin REST Controller for user-scoped cart operations.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartResponseDto>> getCart(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        String userId = resolveUserId(headerUserId, authentication);
        CartResponseDto cart = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cart));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartResponseDto>> saveCart(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody(required = false) Map<String, List<CartItemDto>> payload,
            Authentication authentication) {

        String userId = resolveUserId(headerUserId, authentication);
        List<CartItemDto> items = payload != null ? payload.get("items") : null;
        CartResponseDto cart = cartService.saveCart(userId, items);
        return ResponseEntity.ok(ApiResponse.success("Cart saved successfully", cart));
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartResponseDto>> addItem(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody CartItemDto item,
            Authentication authentication) {

        String userId = resolveUserId(headerUserId, authentication);
        CartResponseDto cart = cartService.addItem(userId, item);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    @PutMapping("/items/{productId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartResponseDto>> updateItemQuantity(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable UUID productId,
            @RequestParam int quantity,
            Authentication authentication) {

        String userId = resolveUserId(headerUserId, authentication);
        CartResponseDto cart = cartService.updateItemQuantity(userId, productId, quantity);
        return ResponseEntity.ok(ApiResponse.success("Cart item quantity updated", cart));
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartResponseDto>> removeItem(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable UUID productId,
            Authentication authentication) {

        String userId = resolveUserId(headerUserId, authentication);
        CartResponseDto cart = cartService.removeItem(userId, productId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CartResponseDto>> clearCart(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        String userId = resolveUserId(headerUserId, authentication);
        CartResponseDto emptyCart = cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully", emptyCart));
    }

    private String resolveUserId(String headerUserId, Authentication authentication) {
        if (headerUserId != null && !headerUserId.trim().isEmpty()) {
            return headerUserId.trim();
        }
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName().trim();
        }
        return "anonymous";
    }
}
