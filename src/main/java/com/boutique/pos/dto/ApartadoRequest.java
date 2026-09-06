package com.boutique.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Payload público para solicitar un apartado ({@code POST /api/public/tiendas/{slug}/apartados},
 * sin autenticación). Solo pide lo mínimo para poder contactar al cliente — sin cuenta,
 * sin login, sin capturar ningún descuento (eso lo decide el cajero al confirmar, nunca
 * el cliente público — ver {@link ApartadoConfirmRequest}).
 */
@Data
public class ApartadoRequest {
    @NotBlank
    private String customerName;

    @NotBlank(message = "El teléfono es obligatorio")
    private String customerPhone;
    private String customerEmail;
    private String notes;

    @NotEmpty(message = "Agrega al menos un producto")
    @Valid
    private List<ApartadoItemRequest> items;
}
