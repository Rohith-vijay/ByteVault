package com.bytevault.product.controller;

import com.bytevault.product.dto.CreateProductRequest;
import com.bytevault.product.dto.ProductResponse;
import com.bytevault.product.entity.ProductStatus;
import com.bytevault.product.entity.ProductType;
import com.bytevault.product.security.VendorAuthorizationService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VendorProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private VendorAuthorizationService vendorAuthorizationService;

    @InjectMocks
    private VendorProductController vendorProductController;

    private UUID vendorId;
    private Authentication vendorAuth;

    @BeforeEach
    void setUp() {
        vendorId = UUID.randomUUID();
        vendorAuth = new UsernamePasswordAuthenticationToken(
                vendorId.toString(),
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR"))
        );
    }

    @Test
    @DisplayName("Approved vendor can create draft product")
    void testApprovedVendorCanCreateDraftProduct() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Test Digital Item")
                .price(BigDecimal.valueOf(10.00))
                .productType(ProductType.DIGITAL)
                .build();

        ProductResponse mockRes = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("Test Digital Item")
                .status(ProductStatus.DRAFT)
                .vendorId(vendorId)
                .build();

        when(productService.createVendorProduct(eq(vendorId), eq(req), eq(false))).thenReturn(mockRes);

        ResponseEntity<ProductResponse> response = vendorProductController.createVendorProduct(req, false, null, vendorAuth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ProductStatus.DRAFT, response.getBody().getStatus());
        verify(vendorAuthorizationService).assertVendorApproved(eq(vendorId), eq(vendorAuth));
    }

    @Test
    @DisplayName("Approved vendor can directly publish product")
    void testApprovedVendorCanDirectlyPublish() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Direct Live Hardware")
                .price(BigDecimal.valueOf(99.00))
                .productType(ProductType.PHYSICAL)
                .sku("BV-DIR-01")
                .build();

        ProductResponse mockRes = ProductResponse.builder()
                .id(UUID.randomUUID())
                .name("Direct Live Hardware")
                .status(ProductStatus.PUBLISHED)
                .vendorId(vendorId)
                .build();

        when(productService.createVendorProduct(eq(vendorId), eq(req), eq(true))).thenReturn(mockRes);

        ResponseEntity<ProductResponse> response = vendorProductController.createVendorProduct(req, true, null, vendorAuth);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ProductStatus.PUBLISHED, response.getBody().getStatus());
        verify(vendorAuthorizationService).assertVendorApproved(eq(vendorId), eq(vendorAuth));
    }

    @Test
    @DisplayName("Unapproved vendor is denied product creation")
    void testUnapprovedVendorIsBlocked() {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("Blocked Item")
                .price(BigDecimal.valueOf(10.00))
                .productType(ProductType.DIGITAL)
                .build();

        doThrow(new AccessDeniedException("Vendor not approved"))
                .when(vendorAuthorizationService).assertVendorApproved(eq(vendorId), eq(vendorAuth));

        assertThrows(AccessDeniedException.class, () ->
                vendorProductController.createVendorProduct(req, true, null, vendorAuth));
    }

    @Test
    @DisplayName("Approved vendor can publish own product")
    void testPublishVendorProduct() {
        UUID productId = UUID.randomUUID();
        ProductResponse mockRes = ProductResponse.builder()
                .id(productId)
                .status(ProductStatus.PUBLISHED)
                .build();

        when(productService.publishVendorProduct(eq(vendorId), eq(productId))).thenReturn(mockRes);

        ResponseEntity<ProductResponse> response = vendorProductController.publishVendorProduct(productId, null, vendorAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ProductStatus.PUBLISHED, response.getBody().getStatus());
        verify(vendorAuthorizationService).assertVendorApproved(eq(vendorId), eq(vendorAuth));
    }

    @Test
    @DisplayName("Approved vendor can deactivate own product")
    void testDeactivateVendorProduct() {
        UUID productId = UUID.randomUUID();
        ProductResponse mockRes = ProductResponse.builder()
                .id(productId)
                .status(ProductStatus.DEACTIVATED)
                .build();

        when(productService.deactivateVendorProduct(eq(vendorId), eq(productId))).thenReturn(mockRes);

        ResponseEntity<ProductResponse> response = vendorProductController.deactivateVendorProduct(productId, null, vendorAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ProductStatus.DEACTIVATED, response.getBody().getStatus());
        verify(vendorAuthorizationService).assertVendorApproved(eq(vendorId), eq(vendorAuth));
    }
}
