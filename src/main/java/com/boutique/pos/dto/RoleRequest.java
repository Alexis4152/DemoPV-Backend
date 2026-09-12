package com.boutique.pos.dto;

import com.boutique.pos.model.AppSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
    // Máximo alineado a roles.name VARCHAR(40) — angosto a propósito en la base, así que
    // sin este tope un nombre de rol un poco largo truena al guardar con un error crudo.
    @NotBlank
    @Size(max = 40, message = "El nombre del rol no puede tener más de 40 caracteres")
    private String name;
    // Máximo alineado a roles.description VARCHAR(200).
    @Size(max = 200, message = "La descripción no puede tener más de 200 caracteres")
    private String description;
    // Secciones de la app a las que da acceso este rol. Exige al menos una a propósito:
    // el modelo interno sí soporta un rol sin ninguna (RoleService#createSeedRole, uso
    // interno del sembrado inicial, no pasa por este DTO), pero permitirlo desde el
    // formulario de "Roles y Permisos" solo produce "roles fantasma" sin acceso a nada,
    // por accidente (olvidar marcar cualquier sección).
    @NotEmpty(message = "Selecciona al menos una sección")
    private Set<AppSection> sections;
}
