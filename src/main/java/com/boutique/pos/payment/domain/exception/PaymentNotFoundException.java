package com.boutique.pos.payment.domain.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }

    public static PaymentNotFoundException withId(String id) {
        return new PaymentNotFoundException("Transacción de pago no encontrada con id: " + id);
    }

    public static PaymentNotFoundException withOpenpayId(String openpayId) {
        return new PaymentNotFoundException("Transacción no encontrada con ID de Openpay: " + openpayId);
    }
}
