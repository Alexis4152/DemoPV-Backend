package com.boutique.pos.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Alta/edición de una materia prima. {@code stock} solo se usa al crear (stock inicial). */
@Data
public class RawMaterialRequest {
    @NotBlank
    @Size(max = 200, message = "El nombre no puede tener más de 200 caracteres")
    private String name;
    @Size(max = 500, message = "La descripción no puede tener más de 500 caracteres")
    private String description;
    @Size(max = 100, message = "El código de producto no puede tener más de 100 caracteres")
    private String barcode;
    @PositiveOrZero
    @DecimalMax(value = "9999999999.99", message = "El costo no puede ser mayor a 9,999,999,999.99")
    private BigDecimal cost;
    @PositiveOrZero
    @Max(value = 2147483647, message = "El stock no puede ser mayor a 2,147,483,647")
    private Integer stock;
    @PositiveOrZero
    @Max(value = 2147483647, message = "El stock mínimo no puede ser mayor a 2,147,483,647")
    private Integer minStock;
    /** Opcional. */
    private Long categoryId;
}
