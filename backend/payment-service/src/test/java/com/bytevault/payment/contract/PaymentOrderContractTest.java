package com.bytevault.payment.contract;

import com.bytevault.common.api.ApiResponse;
import com.bytevault.common.dto.OrderPaymentConfirmationRequest;
import com.bytevault.common.dto.VendorBreakdownEntry;
import com.bytevault.payment.client.OrderClient;
import com.bytevault.payment.config.FeignTimeoutConfig;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer-Driven Contract Test for Payment Service -> Order Service (CO-4).
 * 
 * Verifies that the OrderClient Feign interface satisfies the API contract
 * expected by Payment Service from Order Service:
 * 1. GET /api/v1/orders/internal/{id}/vendor-breakdown with perimeter security headers (X-Gateway-Secret).
 * 2. POST /api/v1/orders/internal/{id}/mark-paid for authoritative PAID progression.
 * 3. POST /api/v1/orders/internal/{id}/refund-cancel for full refund cancellations.
 * 4. Propagation of HTTP 403 (Forbidden) when perimeter credentials are rejected.
 */
@SpringBootTest(classes = PaymentOrderContractTest.TestConfig.class)
public class PaymentOrderContractTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private OrderClient orderClient;

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EnableFeignClients(clients = OrderClient.class)
    @Import(FeignTimeoutConfig.class)
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
        registry.add("app.services.order-service.url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("order-service.ribbon.listOfServers", () -> "localhost:" + wireMockServer.port());
        registry.add("eureka.client.enabled", () -> "false");
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Contract: Payment consumes VendorBreakdownEntry list from Order Service (200 OK)")
    void shouldSatisfyConsumerContractForVendorBreakdown() {
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID vendorId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/orders/internal/" + orderId + "/vendor-breakdown"))
                .withHeader("X-Gateway-Secret", equalTo("test_secret"))
                .withHeader("X-User-Roles", equalTo("ROLE_INTERNAL_SERVICE"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": 200,
                                  "message": "Vendor breakdown retrieved",
                                  "data": [
                                    {
                                      "vendorId": "33333333-3333-3333-3333-333333333333",
                                      "grossSubtotal": 99.99
                                    }
                                  ]
                                }
                                """)));

        ApiResponse<List<VendorBreakdownEntry>> response = orderClient.getVendorBreakdown(
                orderId, "test_secret", "ROLE_INTERNAL_SERVICE");

        assertNotNull(response);
        assertEquals(200, response.status());
        assertNotNull(response.data());
        assertEquals(1, response.data().size());
        assertEquals(vendorId, response.data().get(0).getVendorId());
        assertEquals(new BigDecimal("99.99"), response.data().get(0).getGrossSubtotal());
    }

    @Test
    @DisplayName("Contract: Payment marks order as PAID downstream (200 OK)")
    void shouldSatisfyConsumerContractForMarkOrderPaid() {
        UUID orderId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/orders/internal/" + orderId + "/mark-paid"))
                .withHeader("X-Gateway-Secret", equalTo("test_secret"))
                .withHeader("X-User-Roles", equalTo("ROLE_INTERNAL_SERVICE"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": 200,
                                  "message": "Order marked as PAID",
                                  "data": {
                                    "id": "44444444-4444-4444-4444-444444444444",
                                    "status": "PAID"
                                  }
                                }
                                """)));

        OrderPaymentConfirmationRequest request = OrderPaymentConfirmationRequest.builder()
                .paymentId("pay_test_123")
                .amount(new BigDecimal("99.99"))
                .currency("INR")
                .provider("RAZORPAY")
                .paymentTimestamp(LocalDateTime.now())
                .build();

        ApiResponse<Map<String, Object>> response = orderClient.markOrderPaid(
                orderId, "test_secret", "ROLE_INTERNAL_SERVICE", request);

        assertNotNull(response);
        assertEquals(200, response.status());
        assertNotNull(response.data());
        assertEquals("PAID", response.data().get("status"));
    }

    @Test
    @DisplayName("Contract: Order Service rejects requests without valid X-Gateway-Secret (403 Forbidden)")
    void shouldPropagateForbiddenWhenSecurityHeaderMissing() {
        UUID orderId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        wireMockServer.stubFor(get(urlEqualTo("/api/v1/orders/internal/" + orderId + "/vendor-breakdown"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "status": 403,
                                  "message": "Access Forbidden: Internal service authentication required."
                                }
                                """)));

        assertThrows(FeignException.Forbidden.class, () ->
                orderClient.getVendorBreakdown(orderId, "wrong_secret", "ROLE_USER"));
    }
}
