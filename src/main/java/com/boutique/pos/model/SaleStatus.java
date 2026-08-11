package com.boutique.pos.model;

/**
 * Estado de una {@link Sale}.
 * <p>
 * Una venta nunca se borra físicamente: cancelarla solo cambia su estado a
 * {@code CANCELLED}, revierte el stock de sus {@link SaleItem} y registra
 * {@code cancelledBy}/{@code cancelledAt}.
 */
public enum SaleStatus {
    /** Venta vigente, ya cobrada y aplicada al inventario y al corte de caja. */
    COMPLETED,
    /** Venta cancelada: su stock fue revertido y no cuenta para los totales activos del corte. */
    CANCELLED
}
