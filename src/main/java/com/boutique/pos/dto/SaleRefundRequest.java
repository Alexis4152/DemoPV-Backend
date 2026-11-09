package com.boutique.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Payload para devolver una venta de un turno ya cerrado ({@code POST /api/sales/{id}/refund},
 * mismo permiso que cancelar: Ventas → Eliminar) — ver {@code SaleService#refund}. Siempre es
 * la venta completa (no hay devoluciones parciales); lo único que se elige por artículo es si
 * el cliente entregó el producto y regresa al inventario.
 */
@Data
public class SaleRefundRequest {
    @NotBlank(message = "Escribe el motivo de la devolución")
    @Size(max = 300, message = "El motivo no puede tener más de 300 caracteres")
    private String reason;

    // Ids de los SaleItem cuyo producto SÍ entregó el cliente (regresan al inventario). Los
    // que no vengan aquí se devuelven sin producto: el stock no cambia, pero queda registrado.
    // null = ninguno regresó.
    private List<Long> returnedItemIds;
    // Qué pasó con cada artículo (regresa / merma / no regresó). Si viene, manda sobre
    // returnedItemIds; los artículos que no vengan aquí regresan al inventario.
    @Valid
    private List<ItemOutcomeRequest> items;
}
