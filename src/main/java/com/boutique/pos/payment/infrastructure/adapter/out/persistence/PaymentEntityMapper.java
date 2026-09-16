package com.boutique.pos.payment.infrastructure.adapter.out.persistence;

import com.boutique.pos.payment.domain.model.CustomerInfo;
import com.boutique.pos.payment.domain.model.PaymentMethodDetails;
import com.boutique.pos.payment.domain.model.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class PaymentEntityMapper {

    public PaymentTransactionEntity toEntity(PaymentTransaction domain) {
        if (domain == null) return null;

        var entity = new PaymentTransactionEntity();
        entity.setId(domain.getId());
        entity.setOrderId(domain.getOrderId());
        entity.setTiendaId(domain.getTiendaId());
        entity.setOpenpayTransactionId(domain.getOpenpayTransactionId());
        entity.setAmount(domain.getAmount());
        entity.setCurrency(domain.getCurrency());
        entity.setMethod(domain.getMethod());
        entity.setStatus(domain.getStatus());
        entity.setDescription(domain.getDescription());
        entity.setAuthorizationCode(domain.getAuthorizationCode());
        entity.setFailureReason(domain.getFailureReason());
        entity.setRefundedAmount(domain.getRefundedAmount());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        if (domain.getMethodDetails() != null) {
            entity.setReference(domain.getMethodDetails().reference());
            entity.setBarcodeUrl(domain.getMethodDetails().barcodeUrl());
            entity.setClabe(domain.getMethodDetails().clabe());
            entity.setBank(domain.getMethodDetails().bank());
            entity.setRedirectUrl(domain.getMethodDetails().redirectUrl());
        }

        if (domain.getCustomer() != null) {
            entity.setCustomerName(domain.getCustomer().name());
            entity.setCustomerLastName(domain.getCustomer().lastName());
            entity.setCustomerEmail(domain.getCustomer().email());
            entity.setCustomerPhoneNumber(domain.getCustomer().phoneNumber());
        }

        return entity;
    }

    public PaymentTransaction toDomain(PaymentTransactionEntity entity) {
        if (entity == null) return null;

        var details = new PaymentMethodDetails(
                entity.getReference(),
                entity.getBarcodeUrl(),
                entity.getClabe(),
                entity.getBank(),
                entity.getRedirectUrl()
        );

        CustomerInfo customer = null;
        if (entity.getCustomerEmail() != null || entity.getCustomerName() != null) {
            customer = new CustomerInfo(
                    entity.getCustomerName(),
                    entity.getCustomerLastName(),
                    entity.getCustomerEmail(),
                    entity.getCustomerPhoneNumber()
            );
        }

        return new PaymentTransaction(
                entity.getId(),
                entity.getOrderId(),
                entity.getTiendaId(),
                entity.getOpenpayTransactionId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getMethod(),
                entity.getStatus(),
                entity.getDescription(),
                entity.getAuthorizationCode(),
                details,
                entity.getFailureReason(),
                entity.getRefundedAmount(),
                customer,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
