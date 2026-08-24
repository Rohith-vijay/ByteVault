package com.bytevault.product.controller;

import com.bytevault.product.dto.CreateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PostMapping("/{id}/assets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> uploadDigitalAsset(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws Exception {
        
        return ResponseEntity.ok(productService.uploadDigitalAsset(
                id,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream()
        ));
    }

    // Internal endpoint for fulfillment-service entitlement download resolution
    @GetMapping("/{id}/download-url")
    public ResponseEntity<String> getDownloadUrlInternal(
            @PathVariable UUID id,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret) throws Exception {
        // Secure communication verify
        return ResponseEntity.ok(productService.getDownloadUrl(id));
    }
}
