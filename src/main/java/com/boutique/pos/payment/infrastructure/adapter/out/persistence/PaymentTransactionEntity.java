package com.boutique.pos.payment.infrastructure.adapter.out.persistence;

import com.boutique.pos.payment.domain.model.PaymentMethod;
import com.boutique.pos.payment.domain.model.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_openpay_id", columnList = "openpay_transaction_id"),
        @Index(name = "idx_payment_tienda_id", columnList = "tienda_id")
})
public class PaymentTransactionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "tienda_id")
    private Long tiendaId;

    @Column(name = "openpay_transaction_id", length = 100)
    private String openpayTransactionId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "authorization_code", length = 50)
    private String authorizationCode;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "barcode_url", length = 500)
    private String barcodeUrl;

    @Column(name = "clabe", length = 50)
    private String clabe;

    @Column(name = "bank", length = 100)
    private String bank;

    @Column(name = "redirect_url", length = 500)
    private String redirectUrl;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_last_name", length = 100)
    private String customerLastName;

    @Column(name = "customer_email", length = 150)
    private String customerEmail;

    @Column(name = "customer_phone_number", length = 30)
    private String customerPhoneNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PaymentTransactionEntity() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Long getTiendaId() { return tiendaId; }
    public void setTiendaId(Long tiendaId) { this.tiendaId = tiendaId; }

    public String getOpenpayTransactionId() { return openpayTransactionId; }
    public void setOpenpayTransactionId(String openpayTransactionId) { this.openpayTransactionId = openpayTransactionId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getBarcodeUrl() { return barcodeUrl; }
    public void setBarcodeUrl(String barcodeUrl) { this.barcodeUrl = barcodeUrl; }

    public String getClabe() { return clabe; }
    public void setClabe(String clabe) { this.clabe = clabe; }

    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerLastName() { return customerLastName; }
    public void setCustomerLastName(String customerLastName) { this.customerLastName = customerLastName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhoneNumber() { return customerPhoneNumber; }
    public void setCustomerPhoneNumber(String customerPhoneNumber) { this.customerPhoneNumber = customerPhoneNumber; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
