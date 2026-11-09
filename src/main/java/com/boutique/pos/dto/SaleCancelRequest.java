package com.boutique.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Payload para cancelar una venta del turno abierto ({@code POST /api/sales/{id}/cancel}) —
 * ver {@code SaleService#cancel}. Por artículo se elige qué pasó con el producto; los que no
 * vengan regresan al inventario. Si alguno no regresa, el motivo es obligatorio.
 */
@Data
public class SaleCancelRequest {
    @Size(max = 300, message = "El motivo no puede tener más de 300 caracteres")
    private String reason;

    @Valid
    private List<ItemOutcomeRequest> items;
}
