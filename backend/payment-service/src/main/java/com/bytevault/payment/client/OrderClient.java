package com.bytevault.payment.client;

import com.bytevault.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderClient {

    @PutMapping("/api/v1/orders/{id}/status")
    ApiResponse<Map<String, Object>> updateOrderStatus(
            @PathVariable("id") UUID id,
            @RequestParam("status") String status);
}
