package com.bytevault.product.service;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.product.dto.CreateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.dto.UpdateProductRequest;
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
import java.math.BigDecimal;
import java.time.Instant;
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

    // --- VENDOR OPERATIONS (Approved Vendors Only) ---

    @Transactional
    public ProductResponse createVendorProduct(UUID vendorId, CreateProductRequest request, boolean publish) {
        log.info("[ProductService] Vendor {} creating product: name={}, type={}, publishNow={}",
                vendorId, request.getName(), request.getProductType(), publish);

        validateProductCreation(request);

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        }

        String resolvedSku = resolveSku(request);

        ProductStatus targetStatus = publish ? ProductStatus.PUBLISHED : ProductStatus.DRAFT;
        if (request.getStatus() != null && !publish) {
            if (request.getStatus() == ProductStatus.PUBLISHED || request.getStatus() == ProductStatus.DRAFT) {
                targetStatus = request.getStatus();
            }
        }

        Product product = Product.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(request.getCurrency() != null && !request.getCurrency().trim().isEmpty() ? request.getCurrency().trim() : "INR")
                .sku(resolvedSku)
                .productType(request.getProductType())
                .status(targetStatus)
                .vendorId(vendorId)
                .category(category)
                .tags(request.getTags())
                // Digital metadata
                .fileStorageKey(request.getProductType() == ProductType.DIGITAL
                        ? "assets/" + (request.getFileName() != null && !request.getFileName().trim().isEmpty() ? request.getFileName().trim() : "package.zip")
                        : null)
                .fileName(request.getFileName() != null ? request.getFileName() : (request.getProductType() == ProductType.DIGITAL ? "package.zip" : null))
                .fileType(request.getFileType() != null ? request.getFileType() : (request.getProductType() == ProductType.DIGITAL ? "ZIP" : null))
                .fileSize(request.getFileSize() != null && request.getFileSize() > 0 ? request.getFileSize() : (request.getProductType() == ProductType.DIGITAL ? 1048576L : null))
                .fileVersion(request.getFileVersion() != null ? request.getFileVersion() : (request.getProductType() == ProductType.DIGITAL ? "1.0.0" : null))
                // Physical metadata
                .physicalSku(resolvedSku)
                .physicalWeight(request.getPhysicalWeight() != null ? request.getPhysicalWeight() : request.getWeight())
                .physicalDimensions(request.getPhysicalDimensions())
                .weight(request.getWeight())
                .length(request.getLength())
                .width(request.getWidth())
                .height(request.getHeight())
                .shippingClass(request.getShippingClass())
                .build();

        Product saved = productRepository.save(product);
        log.info("[ProductService] Vendor {} successfully created product: id={}, status={}", vendorId, saved.getId(), saved.getStatus());
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse updateVendorProduct(UUID vendorId, UUID productId, UpdateProductRequest request) {
        log.info("[ProductService] Vendor {} updating product {}", vendorId, productId);
        Product product = findAndVerifyVendorOwnership(productId, vendorId);

        if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Product price must be greater than zero.");
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        }

        // Validate SKU change for physical products
        String skuCandidate = request.getSku() != null ? request.getSku().trim() : (request.getPhysicalSku() != null ? request.getPhysicalSku().trim() : null);
        if (skuCandidate != null && !skuCandidate.isEmpty()) {
            if (productRepository.existsBySkuAndIdNot(skuCandidate, productId)) {
                throw new BadRequestException("SKU already in use by another product: " + skuCandidate);
            }
            product.setSku(skuCandidate);
            product.setPhysicalSku(skuCandidate);
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            product.setName(request.getName().trim());
        }
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getCurrency() != null) product.setCurrency(request.getCurrency());
        if (request.getProductType() != null) product.setProductType(request.getProductType());
        if (request.getTags() != null) product.setTags(request.getTags());
        if (request.getCategoryId() != null) product.setCategory(category);

        // Digital metadata updates
        if (request.getFileName() != null) product.setFileName(request.getFileName());
        if (request.getFileType() != null) product.setFileType(request.getFileType());
        if (request.getFileSize() != null) product.setFileSize(request.getFileSize());
        if (request.getFileVersion() != null) product.setFileVersion(request.getFileVersion());
        if (request.getFileChecksum() != null) product.setFileChecksum(request.getFileChecksum());

        // Physical metadata updates
        if (request.getPhysicalWeight() != null) product.setPhysicalWeight(request.getPhysicalWeight());
        if (request.getPhysicalDimensions() != null) product.setPhysicalDimensions(request.getPhysicalDimensions());
        if (request.getWeight() != null) product.setWeight(request.getWeight());
        if (request.getLength() != null) product.setLength(request.getLength());
        if (request.getWidth() != null) product.setWidth(request.getWidth());
        if (request.getHeight() != null) product.setHeight(request.getHeight());
        if (request.getShippingClass() != null) product.setShippingClass(request.getShippingClass());

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse publishVendorProduct(UUID vendorId, UUID productId) {
        log.info("[ProductService] Vendor {} publishing product {}", vendorId, productId);
        Product product = findAndVerifyVendorOwnership(productId, vendorId);

        if (!product.getStatus().canTransitionTo(ProductStatus.PUBLISHED)) {
            throw new BadRequestException("Cannot transition product status from " + product.getStatus() + " to PUBLISHED.");
        }

        // Validate physical requirements prior to publication
        if (product.getProductType() == ProductType.PHYSICAL && (product.getSku() == null || product.getSku().trim().isEmpty())) {
            throw new BadRequestException("Physical product requires a valid SKU before publication.");
        }

        product.setStatus(ProductStatus.PUBLISHED);
        Product saved = productRepository.save(product);
        log.info("[ProductService] Product {} published successfully by vendor {}", productId, vendorId);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse deactivateVendorProduct(UUID vendorId, UUID productId) {
        log.info("[ProductService] Vendor {} deactivating product {}", vendorId, productId);
        Product product = findAndVerifyVendorOwnership(productId, vendorId);

        if (!product.getStatus().canTransitionTo(ProductStatus.DEACTIVATED)) {
            throw new BadRequestException("Cannot transition product status from " + product.getStatus() + " to DEACTIVATED.");
        }

        product.setStatus(ProductStatus.DEACTIVATED);
        Product saved = productRepository.save(product);
        log.info("[ProductService] Product {} deactivated by vendor {}", productId, vendorId);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getVendorProductById(UUID vendorId, UUID productId) {
        Product product = findAndVerifyVendorOwnership(productId, vendorId);
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByVendor(UUID vendorId, ProductStatus statusFilter) {
        List<Product> products = (statusFilter != null)
                ? productRepository.findByVendorIdAndStatus(vendorId, statusFilter)
                : productRepository.findByVendorId(vendorId);

        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // --- ADMIN MODERATION & GOVERNANCE ---

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProductsAdmin(ProductStatus statusFilter) {
        List<Product> list = (statusFilter != null)
                ? productRepository.findByStatus(statusFilter)
                : productRepository.findAll();

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductByIdAdmin(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse takedownProduct(UUID adminId, UUID productId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BadRequestException("Moderation takedown requires a valid reason.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (!product.getStatus().canTransitionTo(ProductStatus.DEACTIVATED)) {
            throw new BadRequestException("Cannot take down product in status: " + product.getStatus());
        }

        product.setStatus(ProductStatus.DEACTIVATED);
        product.setModerationReason(reason.trim());
        product.setModeratedBy(adminId);
        product.setModeratedAt(Instant.now());

        Product saved = productRepository.save(product);
        log.info("[ProductService] Product {} taken down by Admin {}. Reason: {}", productId, adminId, reason);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse reactivateProduct(UUID adminId, UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (!product.getStatus().canTransitionTo(ProductStatus.PUBLISHED)) {
            throw new BadRequestException("Cannot reactivate product from status: " + product.getStatus());
        }

        product.setStatus(ProductStatus.PUBLISHED);
        product.setModerationReason(null);
        product.setModeratedBy(adminId);
        product.setModeratedAt(Instant.now());

        Product saved = productRepository.save(product);
        log.info("[ProductService] Product {} reactivated by Admin {}", productId, adminId);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        // Admin or system fallback creation
        return createVendorProduct(request.getVendorId(), request, true);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + request.getCategoryId()));
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        if (request.getSku() != null) product.setSku(request.getSku());
        if (request.getCurrency() != null) product.setCurrency(request.getCurrency());
        if (request.getProductType() != null) product.setProductType(request.getProductType());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        product.setCategory(category);
        if (request.getTags() != null) product.setTags(request.getTags());

        // Digital metadata
        if (request.getFileName() != null) product.setFileName(request.getFileName());
        if (request.getFileType() != null) product.setFileType(request.getFileType());
        if (request.getFileSize() != null) product.setFileSize(request.getFileSize());
        if (request.getFileVersion() != null) product.setFileVersion(request.getFileVersion());
        if (request.getFileChecksum() != null) product.setFileChecksum(request.getFileChecksum());

        // Physical metadata
        if (request.getPhysicalSku() != null) product.setPhysicalSku(request.getPhysicalSku());
        if (request.getPhysicalWeight() != null) product.setPhysicalWeight(request.getPhysicalWeight());
        if (request.getPhysicalDimensions() != null) product.setPhysicalDimensions(request.getPhysicalDimensions());
        if (request.getWeight() != null) product.setWeight(request.getWeight());
        if (request.getLength() != null) product.setLength(request.getLength());
        if (request.getWidth() != null) product.setWidth(request.getWidth());
        if (request.getHeight() != null) product.setHeight(request.getHeight());
        if (request.getShippingClass() != null) product.setShippingClass(request.getShippingClass());

        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductResponse updateProductStatus(UUID id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (!product.getStatus().canTransitionTo(status)) {
            throw new BadRequestException("Invalid status transition from " + product.getStatus() + " to " + status);
        }

        product.setStatus(status);
        Product saved = productRepository.save(product);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        product.setStatus(ProductStatus.DEACTIVATED);
        productRepository.save(product);
        log.info("[ProductService] Product soft-deleted/deactivated: id={}", id);
    }

    // --- PUBLIC STOREFRONT CATALOG (PUBLISHED Only) ---

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findByStatus(ProductStatus.PUBLISHED)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String query, ProductType type, Long categoryId) {
        List<Product> published = productRepository.findByStatus(ProductStatus.PUBLISHED);
        return published.stream()
                .filter(p -> {
                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.toLowerCase();
                        boolean nameMatch = p.getName() != null && p.getName().toLowerCase().contains(q);
                        boolean descMatch = p.getDescription() != null && p.getDescription().toLowerCase().contains(q);
                        boolean tagMatch = p.getTags() != null && p.getTags().toLowerCase().contains(q);
                        if (!nameMatch && !descMatch && !tagMatch) return false;
                    }
                    if (type != null && !type.equals(p.getProductType())) {
                        return false;
                    }
                    if (categoryId != null && (p.getCategory() == null || !categoryId.equals(p.getCategory().getId()))) {
                        return false;
                    }
                    return true;
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));

        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Product not found or currently unavailable.");
        }

        return mapToResponse(product);
    }

    // --- DIGITAL ASSETS (Module 3 Preparation) ---

    @Transactional
    public ProductResponse uploadDigitalAsset(UUID productId, String fileName, String contentType, long size, InputStream stream) throws Exception {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (product.getProductType() != ProductType.DIGITAL) {
            throw new BadRequestException("Cannot upload digital assets to a physical product.");
        }

        String storageKey = "assets/" + productId + "/" + fileName;
        String uploadedKey = storageService.uploadFile(storageKey, fileName, contentType, size, stream);

        product.setFileStorageKey(uploadedKey);
        product.setFileName(fileName);
        product.setFileType(contentType);
        product.setFileSize(size);
        product.setFileVersion("1.0.0");

        Product saved = productRepository.save(product);
        log.info("[ProductService] Digital asset successfully attached for product {}: key={}", productId, uploadedKey);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID productId) throws Exception {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (product.getProductType() != ProductType.DIGITAL) {
            throw new BadRequestException("No digital asset available for this product.");
        }

        String storageKey = product.getFileStorageKey();
        if (storageKey == null || storageKey.trim().isEmpty()) {
            if (product.getFileName() != null && !product.getFileName().trim().isEmpty()) {
                storageKey = "assets/" + product.getFileName().trim();
            } else {
                storageKey = "assets/" + productId + "/package.zip";
            }
        }

        return storageService.generatePresignedUrl(storageKey, 15);
    }

    public com.bytevault.product.storage.StorageObject downloadAsset(String key) throws Exception {
        return storageService.downloadFile(key);
    }

    // --- HELPER & VALIDATION METHODS ---

    private void validateProductCreation(CreateProductRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BadRequestException("Product name is required.");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Product price must be greater than zero.");
        }
        if (request.getProductType() == null) {
            throw new BadRequestException("Product type (DIGITAL or PHYSICAL) is required.");
        }

        if (request.getProductType() == ProductType.PHYSICAL) {
            String sku = resolveSku(request);
            if (sku == null || sku.trim().isEmpty()) {
                throw new BadRequestException("SKU is required for physical products.");
            }
            if (productRepository.existsBySku(sku.trim())) {
                throw new BadRequestException("SKU already exists in catalog: " + sku.trim());
            }
        }
    }

    private String resolveSku(CreateProductRequest request) {
        if (request.getSku() != null && !request.getSku().trim().isEmpty()) {
            return request.getSku().trim();
        }
        if (request.getPhysicalSku() != null && !request.getPhysicalSku().trim().isEmpty()) {
            return request.getPhysicalSku().trim();
        }
        return null;
    }

    private Product findAndVerifyVendorOwnership(UUID productId, UUID vendorId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));

        if (product.getVendorId() == null || !product.getVendorId().equals(vendorId)) {
            log.warn("[ProductService] Security Alert: Vendor {} attempted unauthorized access on Product {} owned by Vendor {}",
                    vendorId, productId, product.getVendorId());
            throw new BadRequestException("Access denied: You do not own this product.");
        }
        return product;
    }

    private ProductResponse mapToResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .vendorId(p.getVendorId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .currency(p.getCurrency())
                .sku(p.getSku())
                .productType(p.getProductType())
                .status(p.getStatus())
                .categoryId(p.getCategory() != null ? p.getCategory().getId() : null)
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : null)
                .tags(p.getTags())
                .fileName(p.getFileName())
                .fileType(p.getFileType())
                .fileSize(p.getFileSize())
                .fileVersion(p.getFileVersion())
                .physicalSku(p.getPhysicalSku())
                .physicalWeight(p.getPhysicalWeight())
                .physicalDimensions(p.getPhysicalDimensions())
                .weight(p.getWeight())
                .length(p.getLength())
                .width(p.getWidth())
                .height(p.getHeight())
                .shippingClass(p.getShippingClass())
                .moderationReason(p.getModerationReason())
                .moderatedBy(p.getModeratedBy())
                .moderatedAt(p.getModeratedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
