package com.boutique.pos.payment.application.port.out;

import java.math.BigDecimal;

public interface PaymentGatewayPort {

    /**
     * Procesa un cargo hacia la pasarela Openpay (soporta tarjeta, paynet tienda, y spei).
     */
    GatewayChargeResult createCharge(GatewayChargeCommand command);

    /**
     * Consulta el estado actual de una transacción en Openpay.
     */
    GatewayChargeResult getChargeStatus(String openpayTransactionId);

    /**
     * Realiza un reembolso total o parcial sobre una transacción en Openpay.
     */
    GatewayChargeResult refundCharge(String openpayTransactionId, BigDecimal amount, String reason);
}
