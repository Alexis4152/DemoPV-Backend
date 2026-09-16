package com.boutique.pos.payment.application.port.in;

import com.boutique.pos.payment.domain.model.PaymentTransaction;

public interface RefundPaymentUseCase {
    PaymentTransaction execute(String paymentId, RefundCommand command);
}
