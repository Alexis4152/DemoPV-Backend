package com.boutique.pos.payment.application.port.out;

import com.boutique.pos.payment.domain.model.PaymentMethodDetails;
import com.boutique.pos.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;

public record GatewayChargeResult(
        String openpayTransactionId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String authorizationCode,
        PaymentMethodDetails methodDetails,
        String failureReason
) {}
