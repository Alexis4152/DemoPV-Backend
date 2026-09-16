package com.boutique.pos.payment.domain.exception;

public class PaymentDeclinedException extends RuntimeException {

    private final Integer errorCode;
    private final String errorDescription;

    public PaymentDeclinedException(Integer errorCode, String errorDescription) {
        super("El pago fue declinado por la pasarela: " + errorDescription + " (Código: " + errorCode + ")");
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }
}
