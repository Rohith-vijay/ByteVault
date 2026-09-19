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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductLifecycleAndOwnershipTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductService productService;

    private UUID vendorA;
    private UUID vendorB;
    private UUID adminId;
    private UUID productId;
    private Product sampleProductA;

    @BeforeEach
    void setUp() {
        vendorA = UUID.randomUUID();
        vendorB = UUID.randomUUID();
        adminId = UUID.randomUUID();
        productId = UUID.randomUUID();

        sampleProductA = Product.builder()
                .id(productId)
                .vendorId(vendorA)
                .name("Pro Code Suite")
                .description("Developer suite")
                .price(BigDecimal.valueOf(99.00))
                .currency("INR")
                .productType(ProductType.DIGITAL)
                .status(ProductStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("A. Approved vendor creates DRAFT product")
    void testApprovedVendorCanCreateDraftProduct() {
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(productId);
            return p;
        });

        CreateProductRequest req = CreateProductRequest.builder()
                .name("Draft Guide")
                .description("Work in progress guide")
                .price(BigDecimal.valueOf(19.99))
                .productType(ProductType.DIGITAL)
                .build();

        ProductResponse res = productService.createVendorProduct(vendorA, req, false);

        assertNotNull(res);
        assertEquals(ProductStatus.DRAFT, res.getStatus());
        assertEquals(vendorA, res.getVendorId());
        assertEquals("Draft Guide", res.getName());
    }

    @Test
    @DisplayName("B. Approved vendor directly creates PUBLISHED product")
    void testApprovedVendorCanDirectlyPublishProduct() {
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(productId);
            return p;
        });

        CreateProductRequest req = CreateProductRequest.builder()
                .name("Live Hardware Key")
                .description("Security key")
                .price(BigDecimal.valueOf(49.99))
                .productType(ProductType.PHYSICAL)
                .sku("BV-SEC-001")
                .build();

        ProductResponse res = productService.createVendorProduct(vendorA, req, true);

        assertNotNull(res);
        assertEquals(ProductStatus.PUBLISHED, res.getStatus());
        assertEquals(vendorA, res.getVendorId());
        assertEquals("Live Hardware Key", res.getName());
    }

    @Test
    @DisplayName("F. Approved vendor can publish own draft product")
    void testVendorCanPublishOwnDraft() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProductA);

        ProductResponse res = productService.publishVendorProduct(vendorA, productId);

        assertNotNull(res);
        assertEquals(ProductStatus.PUBLISHED, sampleProductA.getStatus());
    }

    @Test
    @DisplayName("G. Vendor can edit own product")
    void testVendorCanEditOwnProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProductA);

        UpdateProductRequest req = UpdateProductRequest.builder()
                .name("Updated Suite Name")
                .price(BigDecimal.valueOf(129.00))
                .build();

        ProductResponse res = productService.updateVendorProduct(vendorA, productId, req);

        assertNotNull(res);
        assertEquals("Updated Suite Name", sampleProductA.getName());
        assertEquals(BigDecimal.valueOf(129.00), sampleProductA.getPrice());
    }

    @Test
    @DisplayName("H. IDOR Prevention: Vendor A cannot edit Vendor B's product")
    void testVendorCannotEditOtherVendorsProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));

        UpdateProductRequest req = UpdateProductRequest.builder()
                .name("Malicious Update")
                .price(BigDecimal.valueOf(1.00))
                .build();

        assertThrows(BadRequestException.class, () ->
                productService.updateVendorProduct(vendorB, productId, req));
    }

    @Test
    @DisplayName("I. IDOR Prevention: Vendor A cannot publish Vendor B's product")
    void testVendorCannotPublishOtherVendorsProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));

        assertThrows(BadRequestException.class, () ->
                productService.publishVendorProduct(vendorB, productId));
    }

    @Test
    @DisplayName("J. IDOR Prevention: Vendor cannot spoof vendorId in CreateProductRequest")
    void testVendorCannotSpoofVendorId() {
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(productId);
            return p;
        });

        // Attacker passes Vendor B in request body, but authenticated session is Vendor A
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Spoof Attempt")
                .price(BigDecimal.valueOf(29.99))
                .productType(ProductType.DIGITAL)
                .vendorId(vendorB)
                .build();

        ProductResponse res = productService.createVendorProduct(vendorA, req, false);

        assertNotNull(res);
        assertEquals(vendorA, res.getVendorId(), "Vendor ID must resolve to authenticated principal, ignoring body spoofing");
    }

    @Test
    @DisplayName("IDOR Prevention: Vendor A cannot view Vendor B's private product")
    void testVendorCannotViewOtherVendorsProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));

        assertThrows(BadRequestException.class, () ->
                productService.getVendorProductById(vendorB, productId));
    }

    @Test
    @DisplayName("IDOR Prevention: Vendor A cannot deactivate Vendor B's product")
    void testVendorCannotDeactivateOtherVendorsProduct() {
        sampleProductA.setStatus(ProductStatus.PUBLISHED);
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));

        assertThrows(BadRequestException.class, () ->
                productService.deactivateVendorProduct(vendorB, productId));
    }

    @Test
    @DisplayName("K. Vendor can deactivate own product")
    void testVendorCanDeactivateOwnProduct() {
        sampleProductA.setStatus(ProductStatus.PUBLISHED);
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProductA);

        ProductResponse res = productService.deactivateVendorProduct(vendorA, productId);

        assertNotNull(res);
        assertEquals(ProductStatus.DEACTIVATED, sampleProductA.getStatus());
    }

    @Test
    @DisplayName("M. Admin can view all products")
    void testAdminCanViewAllProducts() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProductA));

        List<ProductResponse> all = productService.getAllProductsAdmin(null);

        assertEquals(1, all.size());
        assertEquals(productId, all.get(0).getId());
    }

    @Test
    @DisplayName("N & O. Admin can take down published product with reason")
    void testAdminCanTakedownProductWithReason() {
        sampleProductA.setStatus(ProductStatus.PUBLISHED);
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProductA);

        String reason = "Copyright infringement notice from owner";
        ProductResponse res = productService.takedownProduct(adminId, productId, reason);

        assertNotNull(res);
        assertEquals(ProductStatus.DEACTIVATED, sampleProductA.getStatus());
        assertEquals(reason, sampleProductA.getModerationReason());
        assertEquals(adminId, sampleProductA.getModeratedBy());
        assertNotNull(sampleProductA.getModeratedAt());
    }

    @Test
    @DisplayName("Admin takedown without reason is rejected with BadRequestException")
    void testAdminTakedownWithoutReasonRejected() {
        assertThrows(BadRequestException.class, () ->
                productService.takedownProduct(adminId, productId, "   "));
    }

    @Test
    @DisplayName("P. Admin can reactivate eligible product")
    void testAdminCanReactivateProduct() {
        sampleProductA.setStatus(ProductStatus.DEACTIVATED);
        sampleProductA.setModerationReason("Resolved");
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProductA);

        ProductResponse res = productService.reactivateProduct(adminId, productId);

        assertNotNull(res);
        assertEquals(ProductStatus.PUBLISHED, sampleProductA.getStatus());
        assertNull(sampleProductA.getModerationReason());
    }

    @Test
    @DisplayName("Q, R, S, T. Public catalog returns ONLY PUBLISHED products")
    void testPublicCatalogExcludesDraftDeactivatedAndArchived() {
        Product published = Product.builder().id(UUID.randomUUID()).name("Pub").status(ProductStatus.PUBLISHED).build();
        when(productRepository.findByStatus(ProductStatus.PUBLISHED)).thenReturn(List.of(published));

        List<ProductResponse> publicCatalog = productService.getAllProducts();

        assertEquals(1, publicCatalog.size());
        assertEquals("Pub", publicCatalog.get(0).getName());
    }

    @Test
    @DisplayName("Public storefront getProductById rejects non-PUBLISHED products with ResourceNotFoundException")
    void testPublicStorefrontRejectsNonPublishedProducts() {
        // DRAFT
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProductA));
        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(productId));

        // DEACTIVATED
        sampleProductA.setStatus(ProductStatus.DEACTIVATED);
        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(productId));

        // ARCHIVED
        sampleProductA.setStatus(ProductStatus.ARCHIVED);
        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(productId));
    }

    @Test
    @DisplayName("U. Invalid lifecycle transitions are rejected")
    void testInvalidLifecycleTransitionsRejected() {
        Product archivedProduct = Product.builder().id(productId).status(ProductStatus.ARCHIVED).vendorId(vendorA).build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(archivedProduct));

        assertThrows(BadRequestException.class, () ->
                productService.publishVendorProduct(vendorA, productId));
    }

    @Test
    @DisplayName("V. Non-positive price is rejected")
    void testNegativeOrZeroPriceRejected() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Zero Price Item")
                .price(BigDecimal.ZERO)
                .productType(ProductType.DIGITAL)
                .build();

        assertThrows(BadRequestException.class, () ->
                productService.createVendorProduct(vendorA, req, true));
    }

    @Test
    @DisplayName("W. Missing SKU for physical product is rejected")
    void testMissingPhysicalSkuRejected() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Physical Key")
                .price(BigDecimal.valueOf(25.00))
                .productType(ProductType.PHYSICAL)
                .build();

        assertThrows(BadRequestException.class, () ->
                productService.createVendorProduct(vendorA, req, true));
    }

    @Test
    @DisplayName("X. Duplicate SKU for physical product is rejected")
    void testDuplicatePhysicalSkuRejected() {
        when(productRepository.existsBySku("BV-DUP-01")).thenReturn(true);

        CreateProductRequest req = CreateProductRequest.builder()
                .name("Physical Key 2")
                .price(BigDecimal.valueOf(25.00))
                .productType(ProductType.PHYSICAL)
                .sku("BV-DUP-01")
                .build();

        assertThrows(BadRequestException.class, () ->
                productService.createVendorProduct(vendorA, req, true));
    }
}
