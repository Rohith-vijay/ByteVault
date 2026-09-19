package com.bytevault.product.controller;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.product.dto.CreateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.dto.UpdateProductRequest;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.security.VendorAuthorizationService;
import com.bytevault.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/products/vendor")
@RequiredArgsConstructor
public class VendorProductController {

    private final ProductService productService;
    private final VendorAuthorizationService vendorAuthorizationService;

    @GetMapping("/my-products")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<List<ProductResponse>> getMyProducts(
            @RequestParam(value = "status", required = false) ProductStatus status,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication auth) {
        
        UUID vendorId = resolveVendorId(userIdHeader, auth);
        vendorAuthorizationService.assertVendorApproved(vendorId, auth);
        return ResponseEntity.ok(productService.getProductsByVendor(vendorId, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getVendorProduct(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication auth) {
        
        UUID vendorId = resolveVendorId(userIdHeader, auth);
        vendorAuthorizationService.assertVendorApproved(vendorId, auth);
        return ResponseEntity.ok(productService.getVendorProductById(vendorId, id));
    }

    @PostMapping
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createVendorProduct(
            @Valid @RequestBody CreateProductRequest request,
            @RequestParam(value = "publish", defaultValue = "false") boolean publish,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication auth) {
        
        UUID vendorId = resolveVendorId(userIdHeader, auth);
        vendorAuthorizationService.assertVendorApproved(vendorId, auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createVendorProduct(vendorId, request, publish));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateVendorProduct(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateProductRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication auth) {
        
        UUID vendorId = resolveVendorId(userIdHeader, auth);
        vendorAuthorizationService.assertVendorApproved(vendorId, auth);
        return ResponseEntity.ok(productService.updateVendorProduct(vendorId, id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> publishVendorProduct(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication auth) {
        
        UUID vendorId = resolveVendorId(userIdHeader, auth);
        vendorAuthorizationService.assertVendorApproved(vendorId, auth);
        return ResponseEntity.ok(productService.publishVendorProduct(vendorId, id));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('VENDOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> deactivateVendorProduct(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            Authentication auth) {
        
        UUID vendorId = resolveVendorId(userIdHeader, auth);
        vendorAuthorizationService.assertVendorApproved(vendorId, auth);
        return ResponseEntity.ok(productService.deactivateVendorProduct(vendorId, id));
    }

    private UUID resolveVendorId(String header, Authentication auth) {
        // Priority 1: Authenticated security context principal
        if (auth != null && auth.getName() != null && !auth.getName().trim().isEmpty()) {
            try {
                return UUID.fromString(auth.getName().trim());
            } catch (Exception ignored) {}
        }
        // Priority 2: Gateway verified user ID header (internal service mesh)
        if (header != null && !header.trim().isEmpty()) {
            try {
                return UUID.fromString(header.trim());
            } catch (Exception ignored) {}
        }
        throw new BadRequestException("Unauthorized: Unable to resolve authenticated vendor ID.");
    }
}
