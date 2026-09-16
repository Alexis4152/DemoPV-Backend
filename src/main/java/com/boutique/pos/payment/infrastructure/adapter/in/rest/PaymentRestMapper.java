package com.boutique.pos.payment.infrastructure.adapter.in.rest;

import com.boutique.pos.payment.application.port.in.CreatePaymentCommand;
import com.boutique.pos.payment.domain.model.CustomerInfo;
import com.boutique.pos.payment.domain.model.PaymentTransaction;
import com.boutique.pos.payment.infrastructure.adapter.in.rest.dto.CreatePaymentRequest;
import com.boutique.pos.payment.infrastructure.adapter.in.rest.dto.PaymentMethodDetailsResponse;
import com.boutique.pos.payment.infrastructure.adapter.in.rest.dto.PaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentRestMapper {

    public CreatePaymentCommand toCommand(CreatePaymentRequest request, Long tiendaId) {
        if (request == null) return null;

        CustomerInfo customer = null;
        if (request.customer() != null) {
            customer = new CustomerInfo(
                    request.customer().name(),
                    request.customer().lastName(),
                    request.customer().email(),
                    request.customer().phoneNumber()
            );
        }

        return new CreatePaymentCommand(
                request.orderId(),
                tiendaId,
                request.amount(),
                request.currency(),
                request.method(),
                request.description(),
                request.deviceSessionId(),
                request.sourceId(),
                customer
        );
    }

    public PaymentResponse toResponse(PaymentTransaction domain) {
        if (domain == null) return null;

        PaymentMethodDetailsResponse detailsResponse = null;
        if (domain.getMethodDetails() != null) {
            var d = domain.getMethodDetails();
            detailsResponse = new PaymentMethodDetailsResponse(
                    d.reference(),
                    d.barcodeUrl(),
                    d.clabe(),
                    d.bank(),
                    d.redirectUrl()
            );
        }

        return new PaymentResponse(
                domain.getId(),
                domain.getOrderId(),
                domain.getTiendaId(),
                domain.getOpenpayTransactionId(),
                domain.getAmount(),
                domain.getCurrency(),
                domain.getMethod(),
                domain.getStatus(),
                domain.getDescription(),
                domain.getAuthorizationCode(),
                detailsResponse,
                domain.getFailureReason(),
                domain.getRefundedAmount(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
