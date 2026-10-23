package com.bytevault.order.contract;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.config.CommonFeignConfig;
import com.bytevault.order.client.FulfillmentRevokeClient;
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

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer-Driven Contract Test for Order Service -> Fulfillment Service (CO-4).
 * 
 * Verifies that the FulfillmentRevokeClient Feign interface satisfies the API contract
 * expected by Order Service from Fulfillment Service during full refund processing:
 * 1. POST /api/v1/internal/revoke-by-order?orderId={id} with perimeter security header (X-Gateway-Secret).
 * 2. Successful envelope response parsing with revokedCount.
 * 3. Idempotent re-execution with revokedCount 0.
 * 4. Propagation of HTTP 403 Forbidden on perimeter credential failure.
 */
@SpringBootTest(classes = OrderFulfillmentContractTest.TestConfig.class)
public class OrderFulfillmentContractTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private FulfillmentRevokeClient fulfillmentRevokeClient;

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EnableFeignClients(clients = FulfillmentRevokeClient.class)
    @Import(CommonFeignConfig.class)
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
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.services.fulfillment-service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("app.gateway.secret", () -> "contract_test_gateway_secret_123");
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Contract: Order -> Fulfillment revoke-by-order succeeds and injects X-Gateway-Secret")
    void testRevokeEntitlementsByOrder_SuccessContract() {
        UUID orderId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/internal/revoke-by-order?orderId=" + orderId))
                .withHeader("X-Gateway-Secret", equalTo("contract_test_gateway_secret_123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "success": true,
                                  "message": "Entitlements revoked for order",
                                  "data": {
                                    "orderId": "%s",
                                    "revokedCount": 2
                                  }
                                }
                                """.formatted(orderId))));

        ApiResponse<Map<String, Object>> response = fulfillmentRevokeClient.revokeEntitlementsByOrder(orderId);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals("Entitlements revoked for order", response.message());
        assertNotNull(response.data());
        assertEquals(2, ((Number) response.data().get("revokedCount")).intValue());

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/internal/revoke-by-order?orderId=" + orderId))
                .withHeader("X-Gateway-Secret", equalTo("contract_test_gateway_secret_123")));
    }

    @Test
    @DisplayName("Contract: Order -> Fulfillment revoke on already-revoked order returns 0 revokedCount idempotently")
    void testRevokeEntitlementsByOrder_IdempotentZeroCount() {
        UUID orderId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/internal/revoke-by-order?orderId=" + orderId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "success": true,
                                  "message": "Entitlements revoked for order",
                                  "data": {
                                    "orderId": "%s",
                                    "revokedCount": 0
                                  }
                                }
                                """.formatted(orderId))));

        ApiResponse<Map<String, Object>> response = fulfillmentRevokeClient.revokeEntitlementsByOrder(orderId);

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(0, ((Number) response.data().get("revokedCount")).intValue());
    }

    @Test
    @DisplayName("Contract: Order -> Fulfillment returns 403 when perimeter check fails")
    void testRevokeEntitlementsByOrder_Forbidden() {
        UUID orderId = UUID.randomUUID();

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/internal/revoke-by-order?orderId=" + orderId))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "success": false,
                                  "message": "Access Forbidden: Direct calls not allowed."
                                }
                                """)));

        assertThrows(FeignException.Forbidden.class, () ->
                fulfillmentRevokeClient.revokeEntitlementsByOrder(orderId));
    }
}
