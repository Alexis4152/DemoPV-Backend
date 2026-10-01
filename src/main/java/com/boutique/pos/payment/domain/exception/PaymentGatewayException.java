package com.boutique.pos.payment.domain.exception;

public class PaymentGatewayException extends RuntimeException {

    private final Integer httpStatus;
    private final Integer openpayErrorCode;

    public PaymentGatewayException(String message) {
        super(message);
        this.httpStatus = 502;
        this.openpayErrorCode = null;
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 502;
        this.openpayErrorCode = null;
    }

    public PaymentGatewayException(String message, Integer httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.openpayErrorCode = null;
    }

    public PaymentGatewayException(String message, Integer httpStatus, Integer openpayErrorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.openpayErrorCode = openpayErrorCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Integer getOpenpayErrorCode() {
        return openpayErrorCode;
    }
}
