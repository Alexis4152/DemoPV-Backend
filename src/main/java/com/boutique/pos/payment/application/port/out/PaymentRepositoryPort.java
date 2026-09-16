package com.boutique.pos.payment.application.port.out;

import com.boutique.pos.payment.domain.model.PaymentTransaction;

import java.util.Optional;

public interface PaymentRepositoryPort {

    PaymentTransaction save(PaymentTransaction transaction);

    Optional<PaymentTransaction> findById(String id);

    Optional<PaymentTransaction> findByOpenpayTransactionId(String openpayTransactionId);

    Optional<PaymentTransaction> findByOrderId(String orderId);
}
