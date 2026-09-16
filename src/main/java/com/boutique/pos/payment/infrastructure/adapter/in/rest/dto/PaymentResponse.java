package com.boutique.pos.payment.infrastructure.adapter.in.rest.dto;

import com.boutique.pos.payment.domain.model.PaymentMethod;
import com.boutique.pos.payment.domain.model.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta detallada de la transacción de pago")
public record PaymentResponse(
        @Schema(description = "Identificador único interno de la transacción", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "Identificador de la orden o venta", example = "ORD-2026-9812")
        String orderId,

        @Schema(description = "ID de la tienda que originó la transacción", example = "1")
        Long tiendaId,

        @Schema(description = "Identificador de la transacción asignado por Openpay", example = "trxyz9812401")
        String openpayTransactionId,

        @Schema(description = "Monto de la transacción", example = "250.50")
        BigDecimal amount,

        @Schema(description = "Moneda de la transacción", example = "MXN")
        String currency,

        @Schema(description = "Método de pago utilizado", example = "CARD")
        PaymentMethod method,

        @Schema(description = "Estado actual del pago", example = "COMPLETED")
        PaymentStatus status,

        @Schema(description = "Descripción o concepto", example = "Pago de venta POS")
        String description,

        @Schema(description = "Código de autorización bancaria", example = "891273")
        String authorizationCode,

        @Schema(description = "Detalles de método de pago (tienda, spei o 3D secure)")
        PaymentMethodDetailsResponse paymentMethodDetails,

        @Schema(description = "Motivo de rechazo o error en caso de fallo")
        String failureReason,

        @Schema(description = "Monto acumulado reembolsado", example = "0.00")
        BigDecimal refundedAmount,

        @Schema(description = "Fecha y hora de creación de la transacción")
        Instant createdAt,

        @Schema(description = "Fecha y hora de última actualización")
        Instant updatedAt
) {}
