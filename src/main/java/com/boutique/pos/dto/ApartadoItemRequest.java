package com.boutique.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Una línea dentro de {@link ApartadoRequest}: qué producto y cuánto. */
@Data
public class ApartadoItemRequest {
    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a 0")
    private BigDecimal quantity;
}
