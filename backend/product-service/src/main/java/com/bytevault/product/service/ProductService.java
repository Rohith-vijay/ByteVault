package com.bytevault.product.service;

import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.product.dto.CreateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.entity.Category;
import com.bytevault.product.entity.Product;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import com.bytevault.product.repository.CategoryRepository;
import com.bytevault.product.repository.ProductRepository;
import com.bytevault.product.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("[ProductService] Creating product: name={}, type={}", request.getName(), request.getProductType());
        
        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .productType(request.getProductType())
                .status(ProductStatus.ACTIVE)
                .category(category)
                .fileName(request.getFileName())
                .fileType(request.getFileType())
                .fileSize(request.getFileSize())
                .fileVersion(request.getFileVersion())
                .physicalSku(request.getPhysicalSku())
                .physicalWeight(request.getPhysicalWeight())
                .physicalDimensions(request.getPhysicalDimensions())
                .build();

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findByStatus(ProductStatus.ACTIVE)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse uploadDigitalAsset(UUID productId, String fileName, String contentType, long size, InputStream stream) throws Exception {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (product.getProductType() != ProductType.DIGITAL) {
            throw new IllegalArgumentException("Cannot upload digital assets to a physical product.");
        }

        String storageKey = "assets/" + productId + "/" + fileName;
        String uploadedKey = storageService.uploadFile(storageKey, fileName, contentType, size, stream);

        product.setFileStorageKey(uploadedKey);
        product.setFileName(fileName);
        product.setFileType(contentType);
        product.setFileSize(size);
        product.setFileVersion("1.0.0");

        Product saved = productRepository.save(product);
        log.info("[ProductService] Digital asset successfully uploaded for product {}: key={}", productId, uploadedKey);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID productId) throws Exception {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (product.getProductType() != ProductType.DIGITAL || product.getFileStorageKey() == null) {
            throw new IllegalArgumentException("No digital asset available for this product.");
        }

        // Generate temporary URL (valid for 15 minutes)
        return storageService.generatePresignedUrl(product.getFileStorageKey(), 15);
    }

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .productType(p.getProductType())
                .status(p.getStatus())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .fileName(p.getFileName())
                .fileType(p.getFileType())
                .fileSize(p.getFileSize())
                .fileVersion(p.getFileVersion())
                .physicalSku(p.getPhysicalSku())
                .physicalWeight(p.getPhysicalWeight())
                .physicalDimensions(p.getPhysicalDimensions())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
