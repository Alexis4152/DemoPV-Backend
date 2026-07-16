package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

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
    @PositiveOrZero
    private Integer stock;
    @PositiveOrZero
    private Integer minStock;
    private String unit;
    private Long categoryId;
}
