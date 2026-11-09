package com.boutique.pos.model;

/**
 * Por qué se cancela o se devuelve un apartado con anticipo (ver {@code
 * ApartadoService#retention}). Decide cuánto se queda la tienda de lo que ya pagó el cliente.
 */
public enum ApartadoReturnReason {
    /** El cliente ya no lo quiere: la tienda retiene su porcentaje del total del apartado. */
    CHANGE_OF_MIND,
    /** Producto defectuoso o en mal estado: se le devuelve todo. Solo lo autoriza un
     *  administrador o supervisor, con motivo. */
    DEFECTIVE
}
