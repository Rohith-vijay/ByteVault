package com.bytevault.payment.client;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.dto.OrderPaymentConfirmationRequest;
import com.bytevault.common.dto.VendorBreakdownEntry;
import com.bytevault.payment.config.FeignTimeoutConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "order-service", url = "${app.services.order-service.url:}", configuration = FeignTimeoutConfig.class)
public interface OrderClient {

    @PostMapping("/api/v1/orders/internal/{id}/mark-paid")
    ApiResponse<Map<String, Object>> markOrderPaid(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody OrderPaymentConfirmationRequest request);

    @GetMapping("/api/v1/orders/internal/{id}/vendor-breakdown")
    ApiResponse<List<VendorBreakdownEntry>> getVendorBreakdown(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles);

    @PostMapping("/api/v1/orders/internal/{id}/refund-cancel")
    ApiResponse<Map<String, Object>> refundCancel(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles);

    @PostMapping("/api/v1/orders/internal/{id}/mark-failed")
    ApiResponse<Map<String, Object>> markOrderFailed(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(value = "reason", required = false) String reason);
}
