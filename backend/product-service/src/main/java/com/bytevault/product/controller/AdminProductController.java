package com.bytevault.product.controller;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.product.dto.CreateProductRequest;
import com.bytevault.product.dto.ModerateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.dto.UpdateProductRequest;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.service.ExcelImportService;
import com.bytevault.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final ExcelImportService excelImportService;

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProductResponse>> getAllProductsAdmin(
            @RequestParam(value = "status", required = false) ProductStatus status) {
        return ResponseEntity.ok(productService.getAllProductsAdmin(status));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getProductByIdAdmin(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(productService.getProductByIdAdmin(id));
    }

    @PostMapping("/admin/{id}/takedown")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> takedownProduct(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ModerateProductRequest request,
            Authentication auth) {
        UUID adminId = resolveAdminId(auth);
        return ResponseEntity.ok(productService.takedownProduct(adminId, id, request.getReason()));
    }

    @PostMapping("/admin/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> reactivateProduct(
            @PathVariable("id") UUID id,
            Authentication auth) {
        UUID adminId = resolveAdminId(auth);
        return ResponseEntity.ok(productService.reactivateProduct(adminId, id));
    }

    @PostMapping("/admin/import/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.bytevault.product.dto.ExcelImportPreviewResponse> previewExcel(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(excelImportService.previewExcel(file));
    }

    @PostMapping("/admin/import/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.bytevault.product.dto.ExcelImportResult> confirmExcelImport(
            @Valid @RequestBody com.bytevault.product.dto.ExcelImportConfirmRequest request) {
        return ResponseEntity.ok(excelImportService.confirmImport(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PostMapping("/{id}/assets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> uploadDigitalAsset(
            @PathVariable("id") UUID id,
            @RequestParam("file") MultipartFile file) throws Exception {
        
        return ResponseEntity.ok(productService.uploadDigitalAsset(
                id,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateProductStatus(
            @PathVariable("id") UUID id,
            @RequestParam("status") ProductStatus status) {
        return ResponseEntity.ok(productService.updateProductStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // Internal endpoint for fulfillment-service entitlement download resolution
    @GetMapping("/{id}/download-url")
    public ResponseEntity<String> getDownloadUrlInternal(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Gateway-Secret", required = false) String gatewaySecret) throws Exception {
        return ResponseEntity.ok(productService.getDownloadUrl(id));
    }

    private UUID resolveAdminId(Authentication auth) {
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName().trim());
            } catch (Exception ignored) {}
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
