package com.bytevault.cart.contract;

import com.bytevault.cart.client.ProductClient;
import com.bytevault.cart.config.FeignClientConfig;
import com.bytevault.cart.dto.ProductSummaryResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import feign.FeignException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer-Driven Contract Test for Cart Service -> Product Service (CO-4).
 * 
 * Verifies that the ProductClient Feign interface accurately satisfies the API contract
 * expected by Cart Service from Product Service:
 * 1. GET /api/v1/products/{id} with mandatory perimeter security headers (X-Gateway-Secret).
 * 2. Successful deserialization of ProductSummaryResponse (id, name, price, productType, status).
 * 3. Accurate propagation of HTTP 404 (Not Found) when product does not exist.
 */
@SpringBootTest(classes = CartProductContractTest.TestConfig.class)
public class CartProductContractTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private ProductClient productClient;

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EnableFeignClients(clients = ProductClient.class)
    @Import(FeignClientConfig.class)
    static class TestConfig {
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.services.product-service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("app.gateway.secret", () -> "test_gateway_secret_xyz123");
        registry.add("eureka.client.enabled", () -> "false");
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Contract: Cart consumes ProductSummaryResponse when product exists (200 OK)")
    void shouldSatisfyConsumerContractForProductLookup() {
        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        // Producer Contract Stub:
        // When GET /api/v1/products/{id} is received with X-Gateway-Secret, return 200 with product JSON
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/products/" + productId))
                .withHeader("X-Gateway-Secret", equalTo("test_gateway_secret_xyz123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "11111111-1111-1111-1111-111111111111",
                                  "name": "E-Book: Cloud Architecture Patterns",
                                  "price": 29.99,
                                  "productType": "DIGITAL",
                                  "status": "ACTIVE"
                                }
                                """)));

        // Consumer Action: invoke Feign client
        ProductSummaryResponse response = productClient.getProductById(productId);

        // Verify Consumer Deserialization & Fields
        assertNotNull(response);
        assertEquals(productId, response.getId());
        assertEquals("E-Book: Cloud Architecture Patterns", response.getName());
        assertEquals(new BigDecimal("29.99"), response.getPrice());
        assertEquals("DIGITAL", response.getProductType());
        assertEquals("ACTIVE", response.getStatus());

        // Verify Contract: Request was indeed dispatched with perimeter security header
        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/v1/products/" + productId))
                .withHeader("X-Gateway-Secret", equalTo("test_gateway_secret_xyz123")));
    }

    @Test
    @DisplayName("Contract: Cart receives 404 FeignException when product does not exist")
    void shouldHandleNotFoundContract() {
        UUID nonExistentId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/products/" + nonExistentId))
                .withHeader("X-Gateway-Secret", equalTo("test_gateway_secret_xyz123"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "success": false,
                                  "message": "Product not found with id: 99999999-9999-9999-9999-999999999999"
                                }
                                """)));

        assertThrows(FeignException.NotFound.class, () -> {
            productClient.getProductById(nonExistentId);
        });

        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/v1/products/" + nonExistentId))
                .withHeader("X-Gateway-Secret", equalTo("test_gateway_secret_xyz123")));
    }

    @Test
    @DisplayName("Contract: Cart receives DEACTIVATED product and accurately deserializes status for validation")
    void shouldSatisfyConsumerContractForDeactivatedProduct() {
        UUID productId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/products/" + productId))
                .withHeader("X-Gateway-Secret", equalTo("test_gateway_secret_xyz123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "22222222-2222-2222-2222-222222222222",
                                  "name": "Legacy Video Course",
                                  "price": 19.99,
                                  "productType": "DIGITAL",
                                  "status": "DEACTIVATED"
                                }
                                """)));

        ProductSummaryResponse response = productClient.getProductById(productId);

        assertNotNull(response);
        assertEquals(productId, response.getId());
        assertEquals("Legacy Video Course", response.getName());
        assertEquals(new BigDecimal("19.99"), response.getPrice());
        assertEquals("DIGITAL", response.getProductType());
        assertEquals("DEACTIVATED", response.getStatus());

        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/v1/products/" + productId))
                .withHeader("X-Gateway-Secret", equalTo("test_gateway_secret_xyz123")));
    }
}
