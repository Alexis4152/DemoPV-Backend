package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Confirmación requerida para borrar una tienda de forma permanente (ver {@code
 * TiendaPurgeService#purge}) — el SUPER_ADMIN debe escribir el nombre EXACTO de la tienda,
 * igual que GitHub al borrar un repositorio, para que un borrado nunca ocurra por un clic
 * equivocado o un id copiado mal.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class TiendaPurgeRequest {

    @NotBlank(message = "Debes escribir el nombre de la tienda para confirmar")
    private String confirmedName;
}
