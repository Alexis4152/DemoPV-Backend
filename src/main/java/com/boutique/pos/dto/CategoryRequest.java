package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para crear o actualizar una {@code Category} de productos
 * ({@code POST}/{@code PUT /api/categories/**}, restringido a ADMIN). La categoría creada
 * queda asociada automáticamente a la tienda del actor; se usa para clasificar productos
 * en el inventario y como filtro en el buscador de productos.
 */
@Data
public class CategoryRequest {
    // Máximo alineado a categories.name VARCHAR(100) — sin este tope, un nombre más largo
    // pasa la validación pero truena al guardar con un error crudo de la base de datos.
    @NotBlank
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String name;
    // categories.description es TEXT (sin límite de columna) — este tope es de higiene de
    // la app, no de la base de datos, igual que products.description (ver ProductRequest).
    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres")
    private String description;
}
