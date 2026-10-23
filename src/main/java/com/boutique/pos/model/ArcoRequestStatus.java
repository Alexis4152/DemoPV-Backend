package com.boutique.pos.model;

/**
 * Estado de una {@link ArcoRequest} a lo largo de todo su ciclo de vida legal.
 * <p>
 * Flujo normal: {@code PENDING_EMAIL} (solo canal WEB, mientras no se confirma el correo) →
 * {@code PENDING} → opcionalmente {@code INFO_REQUESTED} → {@code PENDING} de nuevo (al
 * responder) → {@code APPROVED} (ya resuelta a favor, falta ejecutar) → {@code COMPLETED}.
 * Terminales sin ejecución: {@code REJECTED} (resuelta en contra) y {@code NOT_FILED} (el
 * titular no respondió una solicitud de información a tiempo).
 */
public enum ArcoRequestStatus {
    /** Canal WEB únicamente: creada pero el titular todavía no confirma su correo (link mágico). */
    PENDING_EMAIL,
    /** Correo confirmado (o canal ACCOUNT/IN_PERSON, que no lo necesitan): corre el plazo legal. */
    PENDING,
    /** Se le pidió más información al titular; el plazo de respuesta de ÉL corre aparte. */
    INFO_REQUESTED,
    /** Ya se resolvió a su favor (total o parcialmente); falta ejecutar la acción correspondiente. */
    APPROVED,
    /** Resuelta, ejecutada y cerrada. */
    COMPLETED,
    /** Resuelta en contra (improcedente) — terminal, no hay ejecución. */
    REJECTED,
    /** El titular no respondió una solicitud de información dentro del plazo — se cierra sola. */
    NOT_FILED
}
