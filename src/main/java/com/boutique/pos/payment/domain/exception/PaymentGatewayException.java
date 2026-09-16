package com.boutique.pos.payment.domain.exception;

public class PaymentGatewayException extends RuntimeException {

    private final Integer httpStatus;

    public PaymentGatewayException(String message) {
        super(message);
        this.httpStatus = 502;
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 502;
    }

    public PaymentGatewayException(String message, Integer httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
