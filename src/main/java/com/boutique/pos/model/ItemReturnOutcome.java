package com.boutique.pos.model;

/**
 * Qué pasó físicamente con un producto al cancelar una venta, devolverla o cancelar un
 * apartado — lo elige quien lo hace, por artículo (ver {@code StockReturnService}).
 */
public enum ItemReturnOutcome {
    /** Regresó en buen estado: vuelve al stock vendible. */
    RESTOCK,
    /** Regresó pero ya no sirve (dañado, caducado, abierto): entra y sale como merma. */
    WASTE,
    /** No regresó (el cliente se lo quedó o se perdió): el stock no cambia, queda registrado. */
    NOT_RETURNED
}
