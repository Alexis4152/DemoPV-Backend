package com.boutique.pos.model;

/** Resultado legal de una {@link ArcoRequest} al responderla (ver {@code ArcoRequestService#respond}). */
public enum ArcoOutcome {
    /** Procedente: se concede tal como se pidió. */
    PROCEDENTE,
    /** Procedente parcialmente: se concede solo una parte. */
    PARCIAL,
    /** Improcedente: se niega — la solicitud queda {@code REJECTED}, sin ejecución. */
    IMPROCEDENTE
}
