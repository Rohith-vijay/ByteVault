package com.marketplace.seller.controller;

import com.marketplace.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sellers")
public class SellerController {

    @PostMapping("/apply")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> apply(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("username", authentication.getName());
        response.put("businessName", request.get("businessName"));
        response.put("status", "PENDING");

        return ResponseEntity.ok(ApiResponse.success("Seller registration request submitted", response));
    }
}
