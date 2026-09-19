package com.bytevault.product.client;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.product.config.FeignClientConfig;
import com.bytevault.product.dto.VendorStatusSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "${app.services.user-service.url:}", configuration = FeignClientConfig.class)
public interface UserClient {

    @GetMapping("/api/v1/users/internal/vendors/{userId}/status")
    ResponseEntity<ApiResponse<VendorStatusSummaryDto>> getVendorStatus(@PathVariable("userId") UUID userId);
}
