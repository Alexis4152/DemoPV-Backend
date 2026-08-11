package com.boutique.pos.dto;

import com.boutique.pos.model.AppSection;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

/**
 * Payload para crear o actualizar un {@code Role} ({@code POST}/{@code PUT /api/roles/**}).
 * Un {@code Role} pertenece siempre a una sola tienda (no es global): define el conjunto
 * de {@link AppSection} a las que dan acceso los usuarios que lo tienen asignado, es decir,
 * qué módulos de la app (POS, Inventario, Ventas, Cortes de caja, Reportes, Usuarios,
 * Roles, Dashboard) puede ver y usar ese rol.
 */
@Data
public class RoleRequest {
    @NotBlank
    private String name;
    private String description;
    // Secciones de la app a las que da acceso este rol; puede quedar vacío o null,
    // en cuyo caso el rol no da acceso a ningún módulo.
    private Set<AppSection> sections;
}
