package com.bytevault.order.client;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.config.CommonFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

/**
 * Feign client for the Fulfillment Service's internal entitlement-revocation endpoint.
 * Used by Order Service when a full-refund CANCELLED transition is applied — keeps the
 * dependency direction Payment → Order → Fulfillment with no cycles.
 * Uses CommonFeignConfig so X-Gateway-Secret is injected automatically.
 */
@FeignClient(name = "fulfillment-service", contextId = "fulfillmentRevokeClient",
        configuration = CommonFeignConfig.class)
public interface FulfillmentRevokeClient {

    @PostMapping("/api/v1/internal/revoke-by-order")
    ApiResponse<Map<String, Object>> revokeEntitlementsByOrder(
            @RequestParam("orderId") UUID orderId);
}
