package com.boutique.pos.model;

/** Cómo se verificó la identidad de quien pide una {@link ArcoRequest} "de identidad fuerte". */
public enum ArcoIdentityMethod {
    /** Volvió a escribir la contraseña de su cuenta ya autenticada. */
    PASSWORD,
    /** El personal la verificó en persona (ej. identificación mostrada en mostrador). */
    IN_PERSON,
    /** El personal la verificó por teléfono (ej. confirmando datos de la cuenta). */
    PHONE,
    /** Cualquier otro medio, explicado en la nota. */
    OTHER
}
