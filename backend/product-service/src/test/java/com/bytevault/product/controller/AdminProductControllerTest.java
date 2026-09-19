package com.bytevault.product.controller;

import com.bytevault.common.exception.BadRequestException;
import com.bytevault.product.dto.ModerateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.service.ExcelImportService;
import com.bytevault.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private ExcelImportService excelImportService;

    @InjectMocks
    private AdminProductController adminProductController;

    private UUID adminId;
    private Authentication adminAuth;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        adminAuth = new UsernamePasswordAuthenticationToken(
                adminId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    @DisplayName("Admin can get all products with optional status filter")
    void testAdminGetAllProducts() {
        ProductResponse p = ProductResponse.builder().id(UUID.randomUUID()).status(ProductStatus.PUBLISHED).build();
        when(productService.getAllProductsAdmin(ProductStatus.PUBLISHED)).thenReturn(List.of(p));

        ResponseEntity<List<ProductResponse>> response = adminProductController.getAllProductsAdmin(ProductStatus.PUBLISHED);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(productService).getAllProductsAdmin(ProductStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Admin can get single product by ID for moderation review")
    void testAdminGetProductById() {
        UUID productId = UUID.randomUUID();
        ProductResponse p = ProductResponse.builder().id(productId).status(ProductStatus.PUBLISHED).build();
        when(productService.getProductByIdAdmin(productId)).thenReturn(p);

        ResponseEntity<ProductResponse> response = adminProductController.getProductByIdAdmin(productId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(productId, response.getBody().getId());
        verify(productService).getProductByIdAdmin(productId);
    }

    @Test
    @DisplayName("Admin can moderate/takedown product with reason")
    void testAdminTakedownProduct() {
        UUID productId = UUID.randomUUID();
        ModerateProductRequest request = ModerateProductRequest.builder()
                .reason("DMCA violation notice")
                .build();

        ProductResponse responseMock = ProductResponse.builder()
                .id(productId)
                .status(ProductStatus.DEACTIVATED)
                .moderationReason("DMCA violation notice")
                .build();

        when(productService.takedownProduct(eq(adminId), eq(productId), eq("DMCA violation notice")))
                .thenReturn(responseMock);

        ResponseEntity<ProductResponse> response = adminProductController.takedownProduct(productId, request, adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ProductStatus.DEACTIVATED, response.getBody().getStatus());
        assertEquals("DMCA violation notice", response.getBody().getModerationReason());
    }

    @Test
    @DisplayName("Admin can reactivate deactivated product")
    void testAdminReactivateProduct() {
        UUID productId = UUID.randomUUID();
        ProductResponse responseMock = ProductResponse.builder()
                .id(productId)
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productService.reactivateProduct(eq(adminId), eq(productId))).thenReturn(responseMock);

        ResponseEntity<ProductResponse> response = adminProductController.reactivateProduct(productId, adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ProductStatus.PUBLISHED, response.getBody().getStatus());
    }
}
