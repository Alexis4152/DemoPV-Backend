package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload para ajustar manualmente el stock de un producto
 * ({@code POST /api/products/{id}/adjust-stock}). A diferencia del resto de operaciones
 * de inventario (crear/editar producto), este endpoint no requiere rol ADMIN: basta con
 * tener acceso a la sección de Inventario ({@link com.boutique.pos.model.AppSection#INVENTORY}).
 */
@Data
public class InventoryAdjustRequest {
    // Cantidad a sumar (positiva) o restar (negativa) al stock actual del producto;
    // no es el nuevo total, sino el delta a aplicar. Un valor negativo (quitar piezas)
    // solo lo puede aplicar un ADMIN; el servicio lo rechaza para otros roles.
    @NotNull
    private Integer quantity;
    // Motivo del ajuste (opcional), útil para auditar mermas, conteos físicos, etc.
    private String reason;
}
