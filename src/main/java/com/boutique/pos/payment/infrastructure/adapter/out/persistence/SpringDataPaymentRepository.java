package com.boutique.pos.payment.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentTransactionEntity, String> {

    Optional<PaymentTransactionEntity> findByOpenpayTransactionId(String openpayTransactionId);

    Optional<PaymentTransactionEntity> findByOrderId(String orderId);

    List<PaymentTransactionEntity> findAllByTiendaId(Long tiendaId);
}
