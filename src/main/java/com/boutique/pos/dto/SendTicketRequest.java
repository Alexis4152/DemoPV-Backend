package com.boutique.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Correo al que se manda el ticket digital de una venta. */
@Data
public class SendTicketRequest {
    @NotBlank(message = "Captura un correo")
    @Email(message = "Correo inválido")
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String email;
}
