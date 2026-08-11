package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload para crear o actualizar una {@code Category} de productos
 * ({@code POST}/{@code PUT /api/categories/**}, restringido a ADMIN). La categoría creada
 * queda asociada automáticamente a la tienda del actor; se usa para clasificar productos
 * en el inventario y como filtro en el buscador de productos.
 */
@Data
public class CategoryRequest {
    @NotBlank
    private String name;
    private String description;
}
