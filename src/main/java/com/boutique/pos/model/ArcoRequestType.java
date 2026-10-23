package com.boutique.pos.model;

/**
 * Tipo de derecho ARCO (Acceso, Rectificación, Cancelación, Oposición — más Revocación del
 * consentimiento y Limitación de uso/divulgación) que ejerce una {@link ArcoRequest} — ver
 * la Ley Federal de Protección de Datos Personales en Posesión de los Particulares
 * (LFPDPPP). "Cancelación" es lo que en el día a día se dice "borrar mis datos"; se llama
 * así y no {@code DELETION} para que el nombre interno calce con el término legal que lo
 * acompaña en cualquier aviso de privacidad.
 */
public enum ArcoRequestType {
    /** Saber qué datos personales se tienen de la persona y para qué se usan. */
    ACCESS,
    /** Corregir datos personales inexactos o incompletos. */
    RECTIFICATION,
    /** Eliminar/dar de baja los datos personales, cuando proceda. */
    CANCELLATION,
    /** Oponerse a un uso específico de los datos personales. */
    OPPOSITION,
    /** Revocar un consentimiento dado previamente para tratar sus datos. */
    REVOCATION,
    /** Limitar un uso o divulgación específica de sus datos (sin llegar a cancelarlos). */
    LIMITATION;

    /** Texto en español, listo para mostrar en un correo o en pantalla. */
    public String label() {
        return switch (this) {
            case ACCESS -> "Acceso";
            case RECTIFICATION -> "Rectificación";
            case CANCELLATION -> "Cancelación";
            case OPPOSITION -> "Oposición";
            case REVOCATION -> "Revocación";
            case LIMITATION -> "Limitación";
        };
    }

    /**
     * {@code true} si este tipo exige verificar la identidad de quien lo pide con más
     * rigor (contraseña si tiene cuenta en sesión, o verificación explícita del personal si
     * no) antes de poder resolverlo — son los que de verdad exponen o alteran datos
     * sensibles; Oposición/Limitación, en cambio, solo detienen un uso puntual, no exponen
     * ni alteran nada por sí solos.
     */
    public boolean needsStrongIdentity() {
        return this == ACCESS || this == RECTIFICATION || this == CANCELLATION || this == REVOCATION;
    }
}
