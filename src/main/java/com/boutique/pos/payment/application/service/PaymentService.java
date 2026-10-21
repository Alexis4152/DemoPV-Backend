package com.boutique.pos.payment.application.service;

import com.boutique.pos.payment.application.port.in.*;
import com.boutique.pos.payment.application.port.out.GatewayChargeCommand;
import com.boutique.pos.payment.application.port.out.PaymentGatewayPort;
import com.boutique.pos.payment.application.port.out.PaymentRepositoryPort;
import com.boutique.pos.payment.domain.exception.PaymentNotFoundException;
import com.boutique.pos.payment.domain.model.PaymentTransaction;
import com.boutique.pos.model.InventoryMovement;
import com.boutique.pos.model.MovementType;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleItem;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.payment.domain.model.PaymentStatus;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class PaymentService implements CreatePaymentUseCase, GetPaymentStatusUseCase, RefundPaymentUseCase, ProcessWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentRepositoryPort paymentRepositoryPort;
    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    @Autowired
    public PaymentService(PaymentGatewayPort paymentGatewayPort,
                          PaymentRepositoryPort paymentRepositoryPort,
                          SaleRepository saleRepository,
                          ProductService productService,
                          ProductRepository productRepository,
                          InventoryMovementRepository movementRepository) {
        this.paymentGatewayPort = paymentGatewayPort;
        this.paymentRepositoryPort = paymentRepositoryPort;
        this.saleRepository = saleRepository;
        this.productService = productService;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    public PaymentService(PaymentGatewayPort paymentGatewayPort, PaymentRepositoryPort paymentRepositoryPort, SaleRepository saleRepository) {
        this(paymentGatewayPort, paymentRepositoryPort, saleRepository, null, null, null);
    }

    public PaymentService(PaymentGatewayPort paymentGatewayPort, PaymentRepositoryPort paymentRepositoryPort) {
        this(paymentGatewayPort, paymentRepositoryPort, null, null, null, null);
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
    @Transactional(readOnly = true)
    public PaymentTransaction executeByOrderId(String orderId) {
        log.info("Consultando estado de pago con Order ID: {}", orderId);
        return paymentRepositoryPort.findByOrderId(orderId)
                .orElseThrow(() -> PaymentNotFoundException.withOrderId(orderId));
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
            optionalTransaction = paymentRepositoryPort.findById(notification.openpayTransactionId());
        }
        if (optionalTransaction.isEmpty()) {
            log.warn("Webhook recibido para transacción de Openpay desconocida: {}", notification.openpayTransactionId());
            return;
        }

        var transaction = optionalTransaction.get();

        switch (notification.type()) {
            case "charge.succeeded" -> {
                transaction.markAsCompleted(notification.authorizationCode());
                log.info("Transacción {} marcada como COMPLETED vía webhook", transaction.getId());

                if (saleRepository != null && transaction.getOrderId() != null) {
                    saleRepository.findByOrderId(transaction.getOrderId()).ifPresent(sale -> {
                        if (sale.getStatus() == SaleStatus.PENDING) {
                            sale.setStatus(SaleStatus.COMPLETED);
                            saleRepository.save(sale);
                            log.info("Venta ID {} actualizada de PENDING a COMPLETED tras confirmación de pago", sale.getId());
                        }
                    });
                }
            }
            case "charge.failed" -> {
                transaction.markAsFailed(notification.errorMessage() != null ? notification.errorMessage() : "Cargo fallido según webhook");
                log.warn("Transacción {} marcada como FAILED vía webhook", transaction.getId());
            }
            case "charge.refunded" -> {
                if (transaction.getStatus() != PaymentStatus.REFUNDED) {
                    var remaining = transaction.getAmount().subtract(transaction.getRefundedAmount());
                    if (remaining.signum() > 0) {
                        transaction.refund(remaining);
                    }
                }
                log.info("Transacción {} marcada como REFUNDED vía webhook", transaction.getId());
                if (saleRepository != null && transaction.getOrderId() != null) {
                    saleRepository.findByOrderId(transaction.getOrderId()).ifPresent(sale -> {
                        if (sale.getStatus() == SaleStatus.COMPLETED) {
                            revertSaleStock(sale, "Cancelación de venta #" + sale.getId() + " (Webhook Openpay)");
                            sale.setStatus(SaleStatus.CANCELLED);
                            sale.setCancelledAt(LocalDateTime.now());
                            sale.setNotes((sale.getNotes() != null ? sale.getNotes() + "\n" : "") + "[Webhook]: Cargo reembolsado en Openpay");
                            saleRepository.save(sale);
                            log.info("Venta ID {} actualizada a CANCELLED tras reembolso de pago vía webhook", sale.getId());
                        }
                    });
                }
            }
            case "charge.cancelled" -> {
                transaction.markAsCancelled();
                log.info("Transacción {} marcada como CANCELLED vía webhook", transaction.getId());
                if (saleRepository != null && transaction.getOrderId() != null) {
                    saleRepository.findByOrderId(transaction.getOrderId()).ifPresent(sale -> {
                        if (sale.getStatus() == SaleStatus.PENDING) {
                            revertSaleStock(sale, "Cancelación de venta #" + sale.getId() + " (Cargo expirado/cancelado en Openpay)");
                            sale.setStatus(SaleStatus.CANCELLED);
                            sale.setCancelledAt(LocalDateTime.now());
                            sale.setNotes((sale.getNotes() != null ? sale.getNotes() + "\n" : "") + "[Webhook]: Cargo cancelado/expirado en Openpay");
                            saleRepository.save(sale);
                            log.info("Venta ID {} actualizada de PENDING a CANCELLED tras cancelación de cargo en pasarela", sale.getId());
                        }
                    });
                }
            }
            default -> log.info("Evento de webhook no manejado: {}", notification.type());
        }

        paymentRepositoryPort.save(transaction);
    }

    private void revertSaleStock(Sale sale, String reason) {
        if (productService == null || productRepository == null || movementRepository == null || sale.getItems() == null) {
            return;
        }
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() == null) continue;
            Product p = productService.findByIdForUpdate(item.getProduct().getId());
            int qty = item.getQuantity().intValue();
            int previous = p.getStock();
            p.setStock(previous + qty);
            productRepository.save(p);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(p);
            mv.setUser(sale.getUser());
            mv.setType(MovementType.IN);
            mv.setQuantity(qty);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous + qty);
            mv.setReason(reason);
            movementRepository.save(mv);
        }
    }
}
