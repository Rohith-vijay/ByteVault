package com.bytevault.cart.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSummaryResponse {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String productType;
    private String status;

    @JsonSetter("status")
    public void setStatusFromJson(Object val) {
        if (val == null) return;
        if (val instanceof Number || "200".equals(val.toString()) || "201".equals(val.toString())) {
            // Ignore HTTP response status code wrapper
            return;
        }
        this.status = val.toString();
    }

    @JsonProperty("data")
    private void unpackData(Map<String, Object> data) {
        if (data != null) {
            if (data.containsKey("id") && data.get("id") != null) {
                this.id = UUID.fromString(data.get("id").toString());
            }
            if (data.containsKey("name") && data.get("name") != null) {
                this.name = data.get("name").toString();
            }
            if (data.containsKey("price") && data.get("price") != null) {
                this.price = new BigDecimal(data.get("price").toString());
            }
            if (data.containsKey("productType") && data.get("productType") != null) {
                this.productType = data.get("productType").toString();
            }
            if (data.containsKey("status") && data.get("status") != null) {
                this.status = data.get("status").toString();
            }
        }
    }
}
