package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para registrar un ABONO a un apartado con anticipo ({@code POST
 * /api/apartados/{id}/installments}) — ver {@code ApartadoService#addInstallment}. Un pago
 * parcial más, menor al saldo pendiente; para pagar todo el saldo está completar.
 */
@Data
public class ApartadoInstallmentRequest {
    @NotNull(message = "Captura el monto del abono")
    @DecimalMin(value = "0.01", message = "El abono debe ser mayor a 0")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
    private BigDecimal amount;

    @NotNull(message = "Elige el método de pago")
    private PaymentMethod paymentMethod;

    // Con cuánto pagó el cliente; obligatorio si paymentMethod = CASH.
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
    private BigDecimal amountReceived;

    // Si viene, se manda el ticket del abono (PDF) a este correo — igual que al completar.
    @Email
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String customerEmail;
}
