package com.bytevault.shipping;

import com.bytevault.common.security.DownstreamSecurityFilter;
import com.bytevault.shipping.config.SecurityConfig;
import com.bytevault.shipping.controller.ShipmentController;
import com.bytevault.shipping.dto.ShipmentResponseDto;
import com.bytevault.shipping.dto.TrackingResponseDto;
import com.bytevault.shipping.entity.Shipment;
import com.bytevault.shipping.service.ShipmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShipmentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ShipmentEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShipmentService shipmentService;

    @MockBean
    private DownstreamSecurityFilter downstreamSecurityFilter;

    @Test
    @DisplayName("Public tracking lookup succeeds and returns tracking information")
    void testPublicTrackingEndpoint() throws Exception {
        String trackingNum = "TRACK-FDX-123456";
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .trackingNumber(trackingNum)
                .carrier("FedEx")
                .status("IN_TRANSIT")
                .build();

        when(shipmentService.getShipmentByTrackingNumber(trackingNum)).thenReturn(shipment);

        mockMvc.perform(get("/api/v1/shipping/track/" + trackingNum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value(trackingNum))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("Retrieve shipment by orderId returns matching shipment details")
    void testGetShipmentByOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber("TRACK-DHL-999")
                .carrier("DHL")
                .status("SHIPPED")
                .build();

        when(shipmentService.getShipmentByOrderId(orderId)).thenReturn(shipment);

        mockMvc.perform(get("/api/v1/shipping/order/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(orderId.toString()));
    }

    @Test
    @DisplayName("Status update endpoint updates shipment and returns updated state")
    void testUpdateShipmentStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = Shipment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .trackingNumber("TRACK-DHL-999")
                .carrier("DHL")
                .status("DELIVERED")
                .build();

        when(shipmentService.updateShipmentStatus(eq(orderId), eq("DELIVERED"), any(), any())).thenReturn(shipment);

        mockMvc.perform(put("/api/v1/shipping/order/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\",\"location\":\"Front Porch\",\"remarks\":\"Delivered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }
}
