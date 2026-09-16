package com.boutique.pos.payment.infrastructure.adapter.in.rest.dto;

import com.boutique.pos.payment.domain.model.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Solicitud de creación de cargo o intención de pago")
public record CreatePaymentRequest(
        @Schema(description = "Identificador único de orden generado por el comercio", example = "ORD-2026-9812")
        @NotBlank(message = "El identificador de la orden (orderId) es obligatorio")
        @Size(max = 100)
        String orderId,

        @Schema(description = "Monto total a cobrar", example = "250.50")
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "1.00", message = "El monto mínimo permitido es de 1.00")
        BigDecimal amount,

        @Schema(description = "Código de moneda ISO 4217 (MXN, USD, COP)", example = "MXN")
        @NotBlank(message = "La moneda es obligatoria")
        @Pattern(regexp = "^(MXN|USD|COP)$", message = "Moneda no soportada. Permitidas: MXN, USD, COP")
        String currency,

        @Schema(description = "Método de pago a utilizar", example = "CARD")
        @NotNull(message = "El método de pago es obligatorio (CARD, STORE, SPEI)")
        PaymentMethod method,

        @Schema(description = "Concepto o descripción de la compra", example = "Pago de venta POS")
        @NotBlank(message = "La descripción de la compra es obligatoria")
        @Size(max = 250)
        String description,

        @Schema(description = "Identificador de sesión antifraude generado por el dispositivo en el frontend", example = "k92jda01928301293812")
        @NotBlank(message = "El deviceSessionId es obligatorio para prevención de fraude")
        String deviceSessionId,

        @Schema(description = "Token generado para la tarjeta (Obligatorio si method es CARD)", example = "kpt7e08920192")
        String sourceId,

        @Schema(description = "Datos de contacto del cliente")
        @NotNull(message = "Los datos del cliente son obligatorios")
        @Valid
        CustomerRequest customer
) {}
