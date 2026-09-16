package com.boutique.pos.payment.domain.model;

/**
 * Métodos y canales de cobro soportados por la pasarela Openpay.
 */
public enum PaymentMethod {
    /** Pago con tarjeta de crédito o débito (vía token). */
    CARD,
    /** Pago en tiendas de conveniencia de la red Paynet. */
    STORE,
    /** Transferencia electrónica interbancaria SPEI. */
    SPEI
}
