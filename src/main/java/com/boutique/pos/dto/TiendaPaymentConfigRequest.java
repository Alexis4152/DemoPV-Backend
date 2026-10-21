package com.boutique.pos.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Payload exclusivo para {@code SUPER_ADMIN} ({@code PUT /api/tiendas/payment-methods})
 * que permite habilitar o deshabilitar el pago con tarjeta (terminal física) y los
 * pagos mediante la pasarela Openpay para una o N tiendas en una sola operación.
 */
@Data
public class TiendaPaymentConfigRequest {

    @NotEmpty(message = "Debes seleccionar al menos una tienda")
    private List<Long> tiendaIds;

    private Boolean cardPaymentEnabled;

    private Boolean openpayEnabled;
}
