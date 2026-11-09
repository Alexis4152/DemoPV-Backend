package com.boutique.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Revisión del inventario de un apartado que venció o que canceló el cliente desde la tienda
 * en línea ({@code POST /api/apartados/{id}/stock-review}) — ver {@code ApartadoService#reviewStock}.
 */
@Data
public class ApartadoStockReviewRequest {
    // Qué pasó con cada producto (regresa / merma / no regresó). Los que no vengan regresan.
    @Valid
    private List<ItemOutcomeRequest> items;

    // Obligatoria si algún producto no regresa al stock vendible.
    @Size(max = 300, message = "La nota no puede tener más de 300 caracteres")
    private String note;
}
