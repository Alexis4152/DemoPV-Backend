package com.boutique.pos.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Solicitud para cancelar una venta y opcionalmente reembolsar el pago en pasarela")
public record CancelSaleRequest(
        @Schema(description = "Indica si se debe procesar el reembolso en la pasarela de pagos (Openpay)", example = "true")
        Boolean refundPayment,

        @Schema(description = "Monto a reembolsar (opcional, por defecto el total de la venta)", example = "450.00")
        BigDecimal refundAmount,

        @Schema(description = "Motivo de la cancelación / reembolso", example = "Devolución de mercancía por defecto")
        @Size(max = 250, message = "El motivo no puede exceder 250 caracteres")
        String reason
) {}
