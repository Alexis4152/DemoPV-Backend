package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashCutRequest {
    @NotNull @PositiveOrZero
    private BigDecimal amount;
    @PositiveOrZero
    private BigDecimal expenses;
    private String notes;
}
