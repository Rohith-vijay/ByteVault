package com.bytevault.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private List<CartItemDto> items = new ArrayList<>();

    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Builder.Default
    private Integer totalItems = 0;
}
