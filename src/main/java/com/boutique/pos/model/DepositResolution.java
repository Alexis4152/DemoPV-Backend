package com.boutique.pos.model;

/**
 * Qué pasó con el anticipo de un {@link Apartado} {@code DEPOSIT} al cancelarlo (lo decide
 * quien cancela, ver {@code ApartadoService#cancel}).
 */
public enum DepositResolution {
    /** Se le regresó al cliente: la venta del anticipo se canceló (si su turno seguía
     *  abierto) o se registró como devolución (si ya se había cerrado). */
    REFUNDED,
    /** La tienda se lo quedó: la venta del anticipo sigue cobrada. */
    RETAINED
}
