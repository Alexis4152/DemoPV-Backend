package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload para ejecutar una solicitud ya aprobada ({@code POST /{id}/execute}, SUPER_ADMIN). */
@Data
public class ArcoRequestExecute {

    @NotBlank(message = "Describe qué hiciste (queda como constancia)")
    @Size(max = 2000, message = "No puede tener más de 2000 caracteres")
    private String summary;
}
