package com.boutique.pos.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para CANCELAR un apartado {@code PENDING} o {@code ACTIVE}. Todo opcional — un
 * cajero puede cancelar sin dar motivo — pero si {@code reason} viene con algo Y el
 * cliente dejó correo al solicitar el apartado, se le manda un aviso explicándole por qué
 * no se pudo concretar (ver {@code ApartadoService#cancel}/{@code
 * EmailService#sendApartadoCancelledEmail}).
 */
@Data
public class ApartadoCancelRequest {
    // apartados.cancel_reason es TEXT (sin límite de columna) — este tope es de higiene de
    // la app. 500 y no 100 (como cash_cuts.notes) a propósito: esto sí se le manda tal cual
    // al cliente por correo, es una explicación, no una nota interna corta.
    @Size(max = 500, message = "El motivo no puede tener más de 500 caracteres")
    private String reason;
}
