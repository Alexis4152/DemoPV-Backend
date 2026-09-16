package com.boutique.pos.payment.application.port.in;

import com.boutique.pos.payment.domain.model.CustomerInfo;
import com.boutique.pos.payment.domain.model.PaymentMethod;

import java.math.BigDecimal;

public record CreatePaymentCommand(
        String orderId,
        Long tiendaId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String description,
        String deviceSessionId,
        String sourceId,
        CustomerInfo customer
) {}
