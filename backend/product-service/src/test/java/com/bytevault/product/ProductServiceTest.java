package com.bytevault.product;

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
import com.bytevault.product.service.ProductService;
import com.bytevault.product.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private com.bytevault.product.security.VendorAuthorizationService vendorAuthorizationService;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Create digital product with category assignment publishes directly")
    void testCreateDigitalProduct() {
        Category category = Category.builder().id(1L).name("Software").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreateProductRequest request = CreateProductRequest.builder()
                .name("ByteVault IDE Pro")
                .description("Advanced IDE for cloud developers")
                .price(BigDecimal.valueOf(1999.00))
                .productType(ProductType.DIGITAL)
                .categoryId(1L)
                .fileName("bytevault-ide.zip")
                .fileType("application/zip")
                .fileSize(104857600L)
                .fileVersion("2.0.0")
                .build();

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("ByteVault IDE Pro", response.getName());
        assertEquals(ProductType.DIGITAL, response.getProductType());
        assertEquals(ProductStatus.PUBLISHED, response.getStatus());
        assertEquals(1L, response.getCategoryId());
        assertEquals("Software", response.getCategoryName());
    }

    @Test
    @DisplayName("Upload digital asset and generate presigned download URL")
    void testUploadDigitalAssetAndGenerateDownloadUrl() throws Exception {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .name("Developer Handbook")
                .productType(ProductType.DIGITAL)
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(storageService.uploadFile(any(), any(), any(), anyLong(), any())).thenReturn("assets/" + productId + "/handbook.pdf");
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(storageService.generatePresignedUrl(eq("assets/" + productId + "/handbook.pdf"), eq(15)))
                .thenReturn("https://storage.bytevault.io/assets/handbook.pdf?sig=valid15m");

        ProductResponse uploaded = productService.uploadDigitalAsset(
                productId,
                "handbook.pdf",
                "application/pdf",
                2048576L,
                new ByteArrayInputStream(new byte[100]));

        assertNotNull(uploaded);
        assertEquals("assets/" + productId + "/handbook.pdf", product.getFileStorageKey());

        String downloadUrl = productService.getDownloadUrl(productId);
        assertNotNull(downloadUrl);
        assertTrue(downloadUrl.contains("handbook.pdf"));
    }

    @Test
    @DisplayName("Update product details")
    void testUpdateProduct() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .name("Old Name")
                .price(BigDecimal.valueOf(100.00))
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        UpdateProductRequest updateReq = UpdateProductRequest.builder()
                .name("New Updated Name")
                .price(BigDecimal.valueOf(150.00))
                .build();

        ProductResponse updated = productService.updateProduct(productId, updateReq);

        assertEquals("New Updated Name", product.getName());
        assertEquals(BigDecimal.valueOf(150.00), product.getPrice());
    }

    @Test
    @DisplayName("Deactivate product (soft-delete)")
    void testDeleteProduct() {
        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
                .id(productId)
                .name("Product to deactivate")
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.deleteProduct(productId);

        assertEquals(ProductStatus.DEACTIVATED, product.getStatus());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    @DisplayName("Search products by name query and product type filter from PUBLISHED catalog")
    void testSearchProducts() {
        Product p1 = Product.builder().id(UUID.randomUUID()).name("Spring Boot Guide").productType(ProductType.DIGITAL).status(ProductStatus.PUBLISHED).build();
        Product p2 = Product.builder().id(UUID.randomUUID()).name("Microservices Book").productType(ProductType.PHYSICAL).status(ProductStatus.PUBLISHED).build();

        when(productRepository.findByStatus(ProductStatus.PUBLISHED)).thenReturn(List.of(p1, p2));
        when(vendorAuthorizationService.isVendorApproved(any())).thenReturn(true);

        List<ProductResponse> results = productService.searchProducts("Spring", ProductType.DIGITAL, null);

        assertEquals(1, results.size());
        assertEquals("Spring Boot Guide", results.get(0).getName());
    }

    @Test
    @DisplayName("Read gate: getAllProducts filters out products whose vendor is SUSPENDED")
    void testGetAllProducts_FiltersOutSuspendedVendor() {
        UUID approvedVendorId = UUID.randomUUID();
        UUID suspendedVendorId = UUID.randomUUID();

        Product p1 = Product.builder().id(UUID.randomUUID()).name("Active Vendor Product")
                .vendorId(approvedVendorId).productType(ProductType.DIGITAL).status(ProductStatus.PUBLISHED).build();
        Product p2 = Product.builder().id(UUID.randomUUID()).name("Suspended Vendor Product")
                .vendorId(suspendedVendorId).productType(ProductType.DIGITAL).status(ProductStatus.PUBLISHED).build();

        when(productRepository.findByStatus(ProductStatus.PUBLISHED)).thenReturn(List.of(p1, p2));
        when(vendorAuthorizationService.isVendorApproved(approvedVendorId)).thenReturn(true);
        when(vendorAuthorizationService.isVendorApproved(suspendedVendorId)).thenReturn(false);

        List<ProductResponse> results = productService.getAllProducts();

        assertEquals(1, results.size());
        assertEquals("Active Vendor Product", results.get(0).getName());
    }

    @Test
    @DisplayName("Read gate: getProductById throws ResourceNotFoundException if vendor is SUSPENDED")
    void testGetProductById_SuspendedVendor_ThrowsResourceNotFoundException() {
        UUID suspendedVendorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product p = Product.builder().id(productId).name("Suspended Vendor Product")
                .vendorId(suspendedVendorId).productType(ProductType.DIGITAL).status(ProductStatus.PUBLISHED).build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(vendorAuthorizationService.isVendorApproved(suspendedVendorId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(productId));
    }

    @Test
    @DisplayName("Create physical product with SKU, weight, and dimensions")
    void testCreatePhysicalProduct() {
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreateProductRequest request = CreateProductRequest.builder()
                .name("ByteVault T-Shirt")
                .description("100% Organic Cotton")
                .price(BigDecimal.valueOf(799.00))
                .productType(ProductType.PHYSICAL)
                .physicalSku("BV-TSHIRT-BLK-L")
                .physicalWeight(0.25)
                .physicalDimensions("30x20x2 cm")
                .build();

        org.springframework.test.util.ReflectionTestUtils.setField(productService, "physicalProductsEnabled", true);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("ByteVault T-Shirt", response.getName());
        assertEquals(ProductType.PHYSICAL, response.getProductType());
        assertEquals("BV-TSHIRT-BLK-L", response.getPhysicalSku());
        assertEquals(ProductStatus.PUBLISHED, response.getStatus());
    }

    @Test
    @DisplayName("Create product physical type rejected when physicalProductsEnabled is false")
    void testCreateProduct_Physical_DisabledByDefault_ThrowsBadRequestException() {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("ByteVault T-Shirt")
                .description("100% Organic Cotton")
                .price(BigDecimal.valueOf(799.00))
                .productType(ProductType.PHYSICAL)
                .physicalSku("BV-TSHIRT-BLK-L")
                .build();

        org.springframework.test.util.ReflectionTestUtils.setField(productService, "physicalProductsEnabled", false);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productService.createProduct(request));
        assertTrue(ex.getMessage().contains("Physical products are not supported"));
    }

    @Test
    @DisplayName("Request download URL for physical product throws BadRequestException")
    void testGetDownloadUrl_PhysicalProduct_ThrowsBadRequestException() {
        UUID productId = UUID.randomUUID();
        Product physicalProduct = Product.builder()
                .id(productId)
                .name("Hardware Key")
                .productType(ProductType.PHYSICAL)
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productRepository.findById(productId)).thenReturn(Optional.of(physicalProduct));

        assertThrows(BadRequestException.class,
                () -> productService.getDownloadUrl(productId));
    }
}
