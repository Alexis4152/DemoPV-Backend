package com.boutique.pos.payment.application.service;

import com.boutique.pos.payment.application.port.in.*;
import com.boutique.pos.payment.application.port.out.GatewayChargeCommand;
import com.boutique.pos.payment.application.port.out.PaymentGatewayPort;
import com.boutique.pos.payment.application.port.out.PaymentRepositoryPort;
import com.boutique.pos.payment.domain.exception.PaymentNotFoundException;
import com.boutique.pos.payment.domain.model.PaymentTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService implements CreatePaymentUseCase, GetPaymentStatusUseCase, RefundPaymentUseCase, ProcessWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentRepositoryPort paymentRepositoryPort;

    public PaymentService(PaymentGatewayPort paymentGatewayPort, PaymentRepositoryPort paymentRepositoryPort) {
        this.paymentGatewayPort = paymentGatewayPort;
        this.paymentRepositoryPort = paymentRepositoryPort;
    }

    @Override
    public PaymentTransaction execute(CreatePaymentCommand command) {
        log.info("Iniciando creación de pago para orderId: {}, tiendaId: {}, monto: {} {}",
                command.orderId(), command.tiendaId(), command.amount(), command.currency());

        // 1. Crear la transacción inicial en el dominio (Estado PENDING)
        var transaction = PaymentTransaction.createNew(
                command.orderId(),
                command.tiendaId(),
                command.amount(),
                command.currency(),
                command.method(),
                command.description(),
                command.customer()
        );
        transaction = paymentRepositoryPort.save(transaction);

        // 2. Comunicar con el puerto de salida de la pasarela
        var gatewayCommand = new GatewayChargeCommand(
                command.orderId(),
                command.amount(),
                command.currency(),
                command.method(),
                command.description(),
                command.deviceSessionId(),
                command.sourceId(),
                command.customer()
        );

        var gatewayResult = paymentGatewayPort.createCharge(gatewayCommand);

        // 3. Actualizar el estado de la transacción en el dominio con la respuesta de Openpay
        transaction.associateGatewayResult(
                gatewayResult.openpayTransactionId(),
                gatewayResult.status(),
                gatewayResult.authorizationCode(),
                gatewayResult.methodDetails(),
                gatewayResult.failureReason()
        );

        log.info("Pago procesado con ID interno: {}, Openpay ID: {}, Estado: {}",
                transaction.getId(), transaction.getOpenpayTransactionId(), transaction.getStatus());

        return paymentRepositoryPort.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentTransaction execute(String paymentId) {
        log.info("Consultando estado de pago con ID: {}", paymentId);
        return paymentRepositoryPort.findById(paymentId)
                .orElseThrow(() -> PaymentNotFoundException.withId(paymentId));
    }

    @Override
    public PaymentTransaction execute(String paymentId, RefundCommand command) {
        log.info("Iniciando reembolso para paymentId: {}, monto: {}", paymentId, command.amount());

        var transaction = paymentRepositoryPort.findById(paymentId)
                .orElseThrow(() -> PaymentNotFoundException.withId(paymentId));

        // 1. Ejecutar validaciones y reglas de negocio del dominio
        transaction.refund(command.amount());

        // 2. Notificar a la pasarela Openpay
        paymentGatewayPort.refundCharge(
                transaction.getOpenpayTransactionId(),
                command.amount(),
                command.reason()
        );

        log.info("Reembolso aplicado exitosamente a paymentId: {}, nuevo estado: {}", paymentId, transaction.getStatus());
        return paymentRepositoryPort.save(transaction);
    }

    @Override
    public void execute(WebhookNotification notification) {
        log.info("Procesando notificación webhook de tipo: {} para openpayTransactionId: {}",
                notification.type(), notification.openpayTransactionId());

        var optionalTransaction = paymentRepositoryPort.findByOpenpayTransactionId(notification.openpayTransactionId());
        if (optionalTransaction.isEmpty()) {
            log.warn("Webhook recibido para transacción de Openpay desconocida: {}", notification.openpayTransactionId());
            return;
        }

        var transaction = optionalTransaction.get();

        switch (notification.type()) {
            case "charge.succeeded" -> {
                transaction.markAsCompleted(notification.authorizationCode());
                log.info("Transacción {} marcada como COMPLETED vía webhook", transaction.getId());
            }
            case "charge.failed" -> {
                transaction.markAsFailed(notification.errorMessage() != null ? notification.errorMessage() : "Cargo fallido según webhook");
                log.warn("Transacción {} marcada como FAILED vía webhook", transaction.getId());
            }
            case "charge.refunded" -> {
                transaction.refund(transaction.getAmount());
                log.info("Transacción {} marcada como REFUNDED vía webhook", transaction.getId());
            }
            default -> log.info("Evento de webhook no manejado: {}", notification.type());
        }

        paymentRepositoryPort.save(transaction);
    }
}
