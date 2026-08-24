package com.bytevault.cart.controller;

import com.bytevault.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartController {

    private final RedisTemplate<String, Object> redisTemplate;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Object>> getCart(Authentication authentication) {
        String key = "cart:" + authentication.getName();
        Object cart = redisTemplate.opsForValue().get(key);
        if (cart == null) {
            return ResponseEntity.ok(ApiResponse.success("Empty cart retrieved", Collections.emptyMap()));
        }
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cart));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Object>> updateCart(
            Authentication authentication,
            @RequestBody Map<String, Object> items) {
        String key = "cart:" + authentication.getName();
        redisTemplate.opsForValue().set(key, items);
        return ResponseEntity.ok(ApiResponse.success("Cart updated successfully", items));
    }
}
