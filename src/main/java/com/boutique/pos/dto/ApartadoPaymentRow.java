package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.SaleKind;
import com.boutique.pos.model.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un cobro de un apartado (su anticipo, su saldo o su pago completo), para el desglose de
 * pagos en la pantalla de Apartados — ver {@code ApartadoService#payments}.
 */
public record ApartadoPaymentRow(
        Long saleId,
        Long folio,
        SaleKind kind,
        LocalDateTime createdAt,
        PaymentMethod paymentMethod,
        BigDecimal total,
        SaleStatus status,
        LocalDateTime cancelledAt,
        LocalDateTime refundedAt,
        String userName,
        // En un DEPOSIT: true si es un abono, no el anticipo.
        Boolean installment,
        // Devuelto solo en parte (la tienda retuvo el resto). Null = se devolvió todo.
        BigDecimal refundAmount) {
}
