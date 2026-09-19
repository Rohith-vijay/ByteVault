package com.bytevault.product.integration;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.product.entity.Product;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import com.bytevault.product.repository.CategoryRepository;
import com.bytevault.product.repository.ProductRepository;
import com.bytevault.product.service.ProductService;
import com.bytevault.product.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductStorageFulfillmentIntegrationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductService productService;

    private UUID digitalProductId;
    private Product digitalProduct;

    @BeforeEach
    void setUp() {
        digitalProductId = UUID.randomUUID();
        digitalProduct = Product.builder()
                .id(digitalProductId)
                .name("Cloud Architecture Masterclass eBook")
                .description("Comprehensive guide")
                .price(BigDecimal.valueOf(49.99))
                .productType(ProductType.DIGITAL)
                .status(ProductStatus.PUBLISHED)
                .fileStorageKey("assets/" + digitalProductId + "/guide.pdf")
                .fileName("guide.pdf")
                .fileSize(5242880L)
                .fileType("application/pdf")
                .build();
    }

    @Test
    @DisplayName("Digital fulfillment: Generates 15-minute presigned download URL for verified digital purchases")
    void testPresignedDownloadUrlGeneration() throws Exception {
        when(productRepository.findById(digitalProductId)).thenReturn(Optional.of(digitalProduct));
        when(storageService.generatePresignedUrl(eq(digitalProduct.getFileStorageKey()), eq(15)))
                .thenReturn("https://minio.bytevault.internal/media/guide.pdf?X-Amz-Expires=900&sig=abcdef");

        String downloadUrl = productService.getDownloadUrl(digitalProductId);

        assertNotNull(downloadUrl);
        assertTrue(downloadUrl.contains("guide.pdf"));
        assertTrue(downloadUrl.contains("X-Amz-Expires=900"));
        verify(storageService, times(1)).generatePresignedUrl(eq(digitalProduct.getFileStorageKey()), eq(15));
    }

    @Test
    @DisplayName("Physical product download request throws BadRequestException guard")
    void testPhysicalProductDownloadBlocked() throws Exception {
        UUID physicalId = UUID.randomUUID();
        Product physicalProduct = Product.builder()
                .id(physicalId)
                .name("ByteVault YubiKey Neo")
                .productType(ProductType.PHYSICAL)
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productRepository.findById(physicalId)).thenReturn(Optional.of(physicalProduct));

        assertThrows(BadRequestException.class, () -> productService.getDownloadUrl(physicalId));
        verify(storageService, never()).generatePresignedUrl(any(), anyInt());
    }

    @Test
    @DisplayName("Product soft deletion: Sets status to DEACTIVATED preserving historical order relations")
    void testSoftDeletion() {
        when(productRepository.findById(digitalProductId)).thenReturn(Optional.of(digitalProduct));

        productService.deleteProduct(digitalProductId);

        assertEquals(ProductStatus.DEACTIVATED, digitalProduct.getStatus());
        verify(productRepository, times(1)).save(digitalProduct);
    }
}
