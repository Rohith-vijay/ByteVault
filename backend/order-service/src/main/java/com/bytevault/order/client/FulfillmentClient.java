package com.bytevault.order.client;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.config.CommonFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "fulfillment-service", configuration = CommonFeignConfig.class)
public interface FulfillmentClient {

    @PostMapping("/api/v1/internal/fulfill")
    ApiResponse<Void> fulfillOrderInternal(
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_INTERNAL_SERVICE") String roles,
            @RequestParam("orderId") UUID orderId,
            @RequestParam("userId") UUID userId,
            @RequestBody List<UUID> productIds);
}
