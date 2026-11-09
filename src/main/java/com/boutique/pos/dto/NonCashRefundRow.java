package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Devolución con tarjeta o transferencia hecha en un turno ({@code GET
 * /api/cash-cuts/{id}/non-cash-refunds}). No es un movimiento de caja: se muestra junto a ellos
 * como informativa.
 */
public record NonCashRefundRow(Long id, Long folio, BigDecimal amount, PaymentMethod paymentMethod,
                               LocalDateTime refundedAt, String refundedByName,
                               // Para decir qué se devolvió: venta, saldo, anticipo o abono
                               String kind, boolean installment, Long apartadoFolio) {
}
