package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Venta cancelada dentro de un turno, para mostrarla en el corte ({@code GET
 * /api/cash-cuts/{id}/cancelled-sales}). {@code byOther} = la canceló alguien distinto al
 * cajero del turno (p. ej. un administrador): su efectivo esperado bajó sin que él lo hiciera,
 * así que tiene que poder verlo.
 */
public record CancelledSaleRow(Long id, Long folio, BigDecimal total, PaymentMethod paymentMethod,
                               LocalDateTime createdAt, LocalDateTime cancelledAt,
                               String cancelledByName, boolean byOther,
                               // DEPOSIT = anticipo (con su apartado), para mostrarlo como tal
                               String kind, Long apartadoFolio) {
}
