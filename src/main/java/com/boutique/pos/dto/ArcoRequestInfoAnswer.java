package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Payload con lo que el titular responde a una solicitud de información (portal público de seguimiento). */
@Data
public class ArcoRequestInfoAnswer {

    @NotBlank(message = "Escribe tu respuesta")
    @Size(max = 2000, message = "No puede tener más de 2000 caracteres")
    private String answer;
}
