package com.boutique.pos.dto;

import com.boutique.pos.model.StockOutReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para ajustar manualmente el stock de un producto
 * ({@code POST /api/products/{id}/adjust-stock}). Requiere el permiso "Ajustar stock"
 * de Inventario (INVENTORY:ADJUST_STOCK), independiente de "Editar".
 */
@Data
public class InventoryAdjustRequest {
    // Cantidad a sumar (positiva) o restar (negativa) al stock actual del producto;
    // no es el nuevo total, sino el delta a aplicar. Agregar o quitar requiere el permiso
    // "Ajustar stock" (INVENTORY:ADJUST_STOCK); admin/supervisor lo tienen siempre.
    @NotNull
    private Integer quantity;
    // Al agregar piezas: motivo libre (opcional). Al quitar: comentario opcional que se
    // agrega a la etiqueta de `outReason`. Máximo alineado a inventory_movements.reason.
    @Size(max = 200, message = "El comentario no puede tener más de 200 caracteres")
    private String reason;
    // Obligatorio al quitar piezas (quantity negativa): por qué salen. Ignorado al agregar.
    private StockOutReason outReason;
}
