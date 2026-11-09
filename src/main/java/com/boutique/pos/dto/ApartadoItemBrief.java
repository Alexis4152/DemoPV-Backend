package com.boutique.pos.dto;

import com.boutique.pos.model.ApartadoItem;

import java.math.BigDecimal;

/**
 * Un producto del apartado de un anticipo o abono, solo informativo: la venta en sí tiene una
 * sola línea (el pago a cuenta), ver {@code DepositLines}.
 */
public record ApartadoItemBrief(String productName, BigDecimal quantity, BigDecimal unitPrice,
                                BigDecimal discount, BigDecimal subtotal, boolean madeToOrder) {

    public static ApartadoItemBrief of(ApartadoItem i) {
        return new ApartadoItemBrief(i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getDiscount(), i.getSubtotal(),
                Boolean.TRUE.equals(i.getOnOrder())
                        || (i.getProduct() != null && Boolean.TRUE.equals(i.getProduct().getIsMadeToOrder())));
    }
}
