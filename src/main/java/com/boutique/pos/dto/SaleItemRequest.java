package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Una línea (partida) de producto dentro de una venta. Forma parte de la lista
 * {@link SaleRequest#getItems()} enviada al crear una venta en
 * {@code POST /api/sales}; no tiene un endpoint propio.
 * <p>
 * {@code productId} nulo marca una línea "por concepto" (ver POS.jsx "Agregar por
 * concepto") — algo que el cajero vende pero que nunca está en el catálogo/inventario
 * (ej. "2 jitomates"). En ese caso {@code conceptName} y {@code unitPrice} son
 * obligatorios (los captura el cajero a mano) y no hay validación/descuento de stock ni
 * oferta posible, ver {@code SaleService#create}.
 */
@Data
public class SaleItemRequest {
    private Long productId;
    @NotNull @Positive
    private BigDecimal quantity;
    // Descuento aplicado a esta partida (opcional); si es null se trata como cero.
    private BigDecimal discount;
    // El cajero marcó "No quiero promoción" para esta línea (ver POS.jsx) — el producto
    // puede tener una oferta vigente, pero el cliente no quiere aprovecharla (ej. solo
    // quiere 1 pieza de un 2x1); null/false = aplicar la oferta si la hay, como siempre.
    private Boolean skipOffer;
    // Solo para líneas "por concepto" (productId nulo): nombre libre capturado por el
    // cajero, se guarda tal cual en SaleItem.productName y así aparece en el ticket.
    private String conceptName;
    // Solo para líneas "por concepto": precio unitario capturado a mano (no viene de
    // ningún catálogo).
    private BigDecimal unitPrice;
}
