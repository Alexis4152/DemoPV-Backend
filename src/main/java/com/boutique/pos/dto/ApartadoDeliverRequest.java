package com.boutique.pos.dto;

import lombok.Data;

import java.util.List;

/**
 * Entrega (total o parcial) de un apartado pagado completo.
 *
 * <p>{@code itemIds}: las líneas que se entregan ahora (por renglón completo); null entrega todo
 * lo pendiente. {@code deductItemIds}: de esas, las sobre pedido que salen del inventario.</p>
 */
@Data
public class ApartadoDeliverRequest {
    private List<Long> itemIds;
    private List<Long> deductItemIds;
}
