package com.boutique.pos.dto;

import com.boutique.pos.model.ItemReturnOutcome;

import java.math.BigDecimal;

/**
 * Un producto de una venta cancelada o devuelta, para la subtabla de la tabla de movimientos
 * del corte (ver {@code CashCutService#saleItems}).
 *
 * En un anticipo son los productos de su apartado.
 *
 * @param stockKind STOCK (del inventario, con {@code returnOutcome}), CONCEPT (sin producto),
 *                  MADE_TO_ORDER (por pedido); en un anticipo cuyo apartado aún no decide nada:
 *                  RESERVED (sigue apartado), DELIVERED (se entregó) o REVIEW (inventario por revisar)
 */
public record CutSaleItemRow(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal,
                             String stockKind, ItemReturnOutcome returnOutcome) {
}
