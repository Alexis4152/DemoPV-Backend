package com.boutique.pos.payment.infrastructure.adapter.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Solicitud de reembolso total o parcial")
public record RefundRequest(
        @Schema(description = "Monto a reembolsar", example = "100.00")
        @NotNull(message = "El monto a reembolsar es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto mínimo a reembolsar es 0.01")
        BigDecimal amount,

        @Schema(description = "Motivo del reembolso", example = "Devolución de producto")
        @NotBlank(message = "El motivo del reembolso es obligatorio")
        @Size(max = 250)
        String reason
) {}
