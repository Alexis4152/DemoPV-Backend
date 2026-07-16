package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemRequest {
    @NotNull
    private Long productId;
    @NotNull @Positive
    private BigDecimal quantity;
    private BigDecimal discount;
}
