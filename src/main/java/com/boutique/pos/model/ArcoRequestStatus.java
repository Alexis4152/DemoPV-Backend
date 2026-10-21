package com.boutique.pos.model;

/** Estado de una {@link ArcoRequest} a lo largo de su atención por el SUPER_ADMIN. */
public enum ArcoRequestStatus {
    /** Recién recibida, sin atender todavía. */
    PENDING,
    /** Atendida y resuelta a favor de quien la pidió. */
    COMPLETED,
    /** Atendida y rechazada (ej. no se pudo verificar la identidad de quien la pidió). */
    REJECTED
}
