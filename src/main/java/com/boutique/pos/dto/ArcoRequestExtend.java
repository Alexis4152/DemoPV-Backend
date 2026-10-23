package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload para extender una sola vez el plazo legal de respuesta ({@code POST /{id}/extend}, SUPER_ADMIN). */
@Data
public class ArcoRequestExtend {

    @NotBlank(message = "Explica por qué necesitas más plazo")
    @Size(max = 500, message = "No puede tener más de 500 caracteres")
    private String reason;
}
