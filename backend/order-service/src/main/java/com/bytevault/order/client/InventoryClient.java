package com.bytevault.order.client;

import com.bytevault.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/v1/inventory/reserve")
    ApiResponse<Map<String, Object>> reserveStock(
            @RequestParam("productId") UUID productId,
            @RequestParam("quantity") int quantity,
            @RequestParam(value = "orderId", required = false) UUID orderId);

    @PostMapping("/api/v1/inventory/release")
    ApiResponse<Map<String, Object>> releaseStock(
            @RequestParam(value = "orderId", required = false) UUID orderId,
            @RequestParam(value = "productId", required = false) UUID productId,
            @RequestParam(value = "quantity", required = false, defaultValue = "1") int quantity);
}
