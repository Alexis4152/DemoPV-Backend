package com.boutique.pos.payment.domain.model;

import com.boutique.pos.payment.domain.exception.InvalidPaymentOperationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root del dominio de pagos. Encapsula las reglas de negocio, ciclo de vida
 * y transiciones de estado de una transacción con la pasarela Openpay.
 */
public class PaymentTransaction {

    private final String id;
    private final String orderId;
    private final Long tiendaId;
    private String openpayTransactionId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final String description;
    private String authorizationCode;
    private PaymentMethodDetails methodDetails;
    private String failureReason;
    private BigDecimal refundedAmount;
    private final CustomerInfo customer;
    private final Instant createdAt;
    private Instant updatedAt;

    public PaymentTransaction(
            String id,
            String orderId,
            Long tiendaId,
            String openpayTransactionId,
            BigDecimal amount,
            String currency,
            PaymentMethod method,
            PaymentStatus status,
            String description,
            String authorizationCode,
            PaymentMethodDetails methodDetails,
            String failureReason,
            BigDecimal refundedAmount,
            CustomerInfo customer,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "OrderId cannot be null");
        this.tiendaId = tiendaId;
        this.openpayTransactionId = openpayTransactionId;
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        this.method = Objects.requireNonNull(method, "Method cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.description = description;
        this.authorizationCode = authorizationCode;
        this.methodDetails = methodDetails != null ? methodDetails : PaymentMethodDetails.empty();
        this.failureReason = failureReason;
        this.refundedAmount = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
        this.customer = customer;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static PaymentTransaction createNew(
            String orderId,
            Long tiendaId,
            BigDecimal amount,
            String currency,
            PaymentMethod method,
            String description,
            CustomerInfo customer
    ) {
        return new PaymentTransaction(
                UUID.randomUUID().toString(),
                orderId,
                tiendaId,
                null,
                amount,
                currency,
                method,
                PaymentStatus.PENDING,
                description,
                null,
                PaymentMethodDetails.empty(),
                null,
                BigDecimal.ZERO,
                customer,
                Instant.now(),
                Instant.now()
        );
    }

    public void associateGatewayResult(
            String openpayTransactionId,
            PaymentStatus status,
            String authorizationCode,
            PaymentMethodDetails methodDetails,
            String failureReason
    ) {
        this.openpayTransactionId = openpayTransactionId;
        this.status = status;
        this.authorizationCode = authorizationCode;
        if (methodDetails != null) {
            this.methodDetails = methodDetails;
        }
        this.failureReason = failureReason;
        this.updatedAt = Instant.now();
    }

    public void markAsCompleted(String authorizationCode) {
        if (this.status == PaymentStatus.COMPLETED) {
            return;
        }
        this.status = PaymentStatus.COMPLETED;
        this.authorizationCode = authorizationCode;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void refund(BigDecimal amountToRefund) {
        if (this.status != PaymentStatus.COMPLETED && this.status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new InvalidPaymentOperationException(
                    "No se puede reembolsar un pago con estado actual: " + this.status
            );
        }

        BigDecimal newTotalRefunded = this.refundedAmount.add(amountToRefund);
        if (newTotalRefunded.compareTo(this.amount) > 0) {
            throw new InvalidPaymentOperationException(
                    "El monto acumulado a reembolsar (" + newTotalRefunded + ") no puede exceder el monto original (" + this.amount + ")"
            );
        }

        this.refundedAmount = newTotalRefunded;
        this.status = newTotalRefunded.compareTo(this.amount) == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public Long getTiendaId() { return tiendaId; }
    public String getOpenpayTransactionId() { return openpayTransactionId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getAuthorizationCode() { return authorizationCode; }
    public PaymentMethodDetails getMethodDetails() { return methodDetails; }
    public String getFailureReason() { return failureReason; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public CustomerInfo getCustomer() { return customer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
