package com.boutique.pos.dto;

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
    private String reason;
}
