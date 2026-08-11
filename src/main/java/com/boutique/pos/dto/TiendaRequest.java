package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload para crear o actualizar una {@code Tienda} (sucursal/negocio) desde el panel de
 * plataforma ({@code POST}/{@code PUT /api/tiendas/**}, exclusivo de SUPER_ADMIN). Solo
 * cubre el nombre de la tienda; la personalización de marca se maneja aparte con
 * {@link TiendaThemeRequest} y los datos fiscales con {@link TiendaInfoRequest}.
 */
@Data
public class TiendaRequest {
    @NotBlank
    private String name;
}
