package com.bytevault.payment.client;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.dto.OrderPaymentConfirmationRequest;
import com.bytevault.payment.config.FeignTimeoutConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "order-service", configuration = FeignTimeoutConfig.class)
public interface OrderClient {

    @PostMapping("/api/v1/orders/internal/{id}/mark-paid")
    ApiResponse<Map<String, Object>> markOrderPaid(
            @PathVariable("id") UUID id,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret,
            @RequestHeader("X-User-Roles") String userRoles,
            @RequestBody OrderPaymentConfirmationRequest request);

    @org.springframework.web.bind.annotation.PutMapping("/api/v1/orders/{id}/status")
    ApiResponse<Map<String, Object>> updateOrderStatus(
            @PathVariable("id") UUID id,
            @org.springframework.web.bind.annotation.RequestParam("status") String status);
}
