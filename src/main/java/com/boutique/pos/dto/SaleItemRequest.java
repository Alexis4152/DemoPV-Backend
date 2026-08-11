package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Una línea (partida) de producto dentro de una venta. Forma parte de la lista
 * {@link SaleRequest#getItems()} enviada al crear una venta en
 * {@code POST /api/sales}; no tiene un endpoint propio.
 */
@Data
public class SaleItemRequest {
    @NotNull
    private Long productId;
    @NotNull @Positive
    private BigDecimal quantity;
    // Descuento aplicado a esta partida (opcional); si es null se trata como cero.
    private BigDecimal discount;
}
