package com.boutique.pos.dto;

import com.boutique.pos.model.ApartadoReturnReason;
import com.boutique.pos.model.DepositResolution;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import lombok.Data;

/**
 * Payload para CANCELAR un apartado {@code PENDING} o {@code ACTIVE}. Todo opcional — un
 * cajero puede cancelar sin dar motivo — pero si {@code reason} viene con algo Y el
 * cliente dejó correo al solicitar el apartado, se le manda un aviso explicándole por qué
 * no se pudo concretar (ver {@code ApartadoService#cancel}/{@code
 * EmailService#sendApartadoCancelledEmail}).
 */
@Data
public class ApartadoCancelRequest {
    // apartados.cancel_reason es TEXT (sin límite de columna) — este tope es de higiene de
    // la app. 500 y no 100 (como cash_cuts.notes) a propósito: esto sí se le manda tal cual
    // al cliente por correo, es una explicación, no una nota interna corta.
    @Size(max = 500, message = "El motivo no puede tener más de 500 caracteres")
    private String reason;

    // Obligatorio solo si el apartado tiene un anticipo cobrado (lo valida ApartadoService#cancel).
    private DepositResolution depositAction;

    // Apartado con anticipo: cambio de opinión (la tienda retiene su %) o defectuoso (se
    // devuelve todo; solo admin/supervisor, con motivo). Null = cambio de opinión.
    // depositAction = REFUNDED (clientes de antes) cuenta como defectuoso.
    private ApartadoReturnReason returnReason;

    // Apartado activo: qué pasó con cada producto (regresa / merma / no regresó). Los que no
    // vengan regresan al inventario. Si alguno no regresa, stockNote es obligatorio (es nota
    // interna: no se le manda al cliente, a diferencia de reason).
    @Valid
    private List<ItemOutcomeRequest> items;

    @Size(max = 300, message = "La nota no puede tener más de 300 caracteres")
    private String stockNote;
}
