package com.boutique.pos.model;

/** Por dónde entró una {@link ArcoRequest} — determina si hace falta confirmar correo. */
public enum ArcoRequestChannel {
    /** Formulario público, sin sesión: exige confirmar correo (link mágico) antes de que corra el plazo. */
    WEB,
    /** Levantada por un usuario ya autenticado desde "Mis solicitudes": su sesión ya es prueba del correo. */
    ACCOUNT,
    /** Registrada en mostrador por personal, para alguien verificado en persona (con o sin cuenta). */
    IN_PERSON
}
