package com.bytevault.product.controller;

import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "type", required = false) com.bytevault.product.entity.ProductType type,
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        
        if (search != null || type != null || categoryId != null) {
            return ResponseEntity.ok(productService.searchProducts(search, type, categoryId));
        }
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/assets/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAsset(
            @RequestParam("key") String key,
            @RequestParam(value = "token", required = false) String token) throws Exception {
        com.bytevault.product.storage.StorageObject obj = productService.downloadAsset(key);
        org.springframework.core.io.InputStreamResource resource = new org.springframework.core.io.InputStreamResource(obj.getInputStream());
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + obj.getFileName() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(obj.getContentType() != null ? obj.getContentType() : "application/octet-stream"))
                .contentLength(obj.getSize())
                .body(resource);
    }
}
