package com.boutique.pos.dto;

import com.boutique.pos.model.ApartadoReturnReason;
import com.boutique.pos.model.DepositResolution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Payload para devolver un apartado ya completado (el cliente lo recogió y lo regresa) —
 * {@code POST /api/apartados/{id}/refund}, ver {@code ApartadoService#refund}.
 */
@Data
public class ApartadoRefundRequest {
    @NotBlank(message = "Escribe el motivo de la devolución")
    @Size(max = 300, message = "El motivo no puede tener más de 300 caracteres")
    private String reason;

    // Solo si tuvo anticipo cobrado: se le devuelve al cliente o se lo queda la tienda.
    private DepositResolution depositAction;

    // Apartado con anticipo: cambio de opinión (la tienda retiene su %) o defectuoso (se
    // devuelve todo; solo admin/supervisor, con motivo). Null = cambio de opinión.
    // depositAction = REFUNDED (clientes de antes) cuenta como defectuoso.
    private ApartadoReturnReason returnReason;

    // Qué pasó con cada producto (regresa / merma / no regresó). Los que no vengan regresan.
    @Valid
    private List<ItemOutcomeRequest> items;
}
