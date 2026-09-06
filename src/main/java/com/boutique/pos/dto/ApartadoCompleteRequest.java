package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload para COMPLETAR un apartado {@code ACTIVE}: el cliente llegó y pagó, se genera
 * una {@link com.boutique.pos.model.Sale} real con estos datos de cobro — mismo shape que
 * {@link SaleRequest} en la parte de pago, pero sin partidas (ya vienen del apartado) ni
 * stock que descontar de nuevo (ya se descontó al confirmar).
 */
@Data
public class ApartadoCompleteRequest {
    @NotNull
    private PaymentMethod paymentMethod;

    /** Con cuánto pagó el cliente; obligatorio si {@code paymentMethod = CASH} (igual que en {@link SaleRequest}). */
    private BigDecimal amountReceived;

    /**
     * Correo para mandar el ticket digital, opcional — sobreescribe (solo para esta venta,
     * no el registro del apartado) el correo que el cliente haya dejado al solicitarlo por
     * la tienda pública. Pensado para cuando no dejó ninguno ahí pero sí quiere su ticket
     * por correo al recogerlo, o quiere que se le mande a uno distinto.
     */
    private String customerEmail;
}
