package com.boutique.pos.payment.domain.exception;

public class InvalidPaymentOperationException extends RuntimeException {
    public InvalidPaymentOperationException(String message) {
        super(message);
    }
}
