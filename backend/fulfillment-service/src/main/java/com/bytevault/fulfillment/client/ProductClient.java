package com.bytevault.fulfillment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}/download-url")
    ResponseEntity<String> getDownloadUrlInternal(
            @PathVariable("id") UUID id,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret);
}
