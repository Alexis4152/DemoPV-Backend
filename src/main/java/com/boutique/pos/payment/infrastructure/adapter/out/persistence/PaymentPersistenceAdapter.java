package com.boutique.pos.payment.infrastructure.adapter.out.persistence;

import com.boutique.pos.payment.application.port.out.PaymentRepositoryPort;
import com.boutique.pos.payment.domain.model.PaymentTransaction;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PaymentPersistenceAdapter implements PaymentRepositoryPort {

    private final SpringDataPaymentRepository repository;
    private final PaymentEntityMapper mapper;

    public PaymentPersistenceAdapter(SpringDataPaymentRepository repository, PaymentEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        PaymentTransactionEntity entity = mapper.toEntity(transaction);
        PaymentTransactionEntity savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<PaymentTransaction> findById(String id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByOpenpayTransactionId(String openpayTransactionId) {
        return repository.findByOpenpayTransactionId(openpayTransactionId).map(mapper::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId).map(mapper::toDomain);
    }
}
