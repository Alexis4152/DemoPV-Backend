package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para crear o actualizar un {@code Product} del catálogo de la tienda del actor
 * ({@code POST}/{@code PUT /api/products/**}, restringido a ADMIN). Para ajustes puntuales
 * de existencias (entradas/salidas de stock) se usa {@link InventoryAdjustRequest} en su
 * propio endpoint, no este DTO.
 */
@Data
public class ProductRequest {
    @NotBlank
    private String name;
    private String description;
    private String barcode;
    @NotNull @PositiveOrZero
    private BigDecimal price;
    @PositiveOrZero
    private BigDecimal cost;
    // Stock inicial/absoluto del producto (no un delta); los ajustes posteriores de
    // inventario se hacen vía InventoryAdjustRequest, no reenviando este campo.
    @PositiveOrZero
    private Integer stock;
    // Umbral usado para marcar el producto como "stock bajo" en el buscador y en el
    // widget del Dashboard.
    @PositiveOrZero
    private Integer minStock;
    private String unit;
    private Long categoryId;
}
