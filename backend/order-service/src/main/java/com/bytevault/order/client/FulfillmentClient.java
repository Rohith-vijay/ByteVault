package com.bytevault.order.client;

import com.bytevault.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "fulfillment-service")
public interface FulfillmentClient {

    @PostMapping("/api/v1/internal/fulfill")
    ApiResponse<Void> fulfillOrderInternal(
            @RequestHeader("X-Gateway-Secret") String gatewaySecret,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_INTERNAL_SERVICE") String roles,
            @RequestParam("orderId") UUID orderId,
            @RequestParam("userId") UUID userId,
            @RequestBody List<UUID> productIds);
}
