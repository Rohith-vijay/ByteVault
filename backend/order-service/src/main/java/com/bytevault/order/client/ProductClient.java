package com.bytevault.order.client;

import com.bytevault.order.dto.ProductSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    ProductSummaryResponse getProductById(@PathVariable("id") UUID id);
}
