package com.boutique.pos.payment.application.service;

import com.boutique.pos.payment.application.port.in.CreatePaymentCommand;
import com.boutique.pos.payment.application.port.in.RefundCommand;
import com.boutique.pos.payment.application.port.in.WebhookNotification;
import com.boutique.pos.payment.application.port.out.GatewayChargeCommand;
import com.boutique.pos.payment.application.port.out.GatewayChargeResult;
import com.boutique.pos.payment.application.port.out.PaymentGatewayPort;
import com.boutique.pos.payment.application.port.out.PaymentRepositoryPort;
import com.boutique.pos.payment.domain.exception.InvalidPaymentOperationException;
import com.boutique.pos.payment.domain.exception.PaymentNotFoundException;
import com.boutique.pos.payment.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private PaymentRepositoryPort paymentRepositoryPort;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentGatewayPort, paymentRepositoryPort);
    }

    @Test
    @DisplayName("Debe procesar exitosamente un pago con tarjeta y retornar la transacción en estado COMPLETED")
    void shouldProcessCardPaymentSuccessfully() {
        // Arrange
        var customer = new CustomerInfo("Carlos", "Santana", "carlos@example.com", "5512345678");
        var command = new CreatePaymentCommand(
                "ORD-100",
                1L,
                new BigDecimal("500.00"),
                "MXN",
                PaymentMethod.CARD,
                "Venta POS Mostrador",
                "device-session-123",
                "token-card-abc",
                customer
        );

        when(paymentRepositoryPort.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var gatewayResult = new GatewayChargeResult(
                "tr-openpay-999",
                PaymentStatus.COMPLETED,
                new BigDecimal("500.00"),
                "MXN",
                "AUTH-123456",
                PaymentMethodDetails.empty(),
                null
        );
        when(paymentGatewayPort.createCharge(any(GatewayChargeCommand.class))).thenReturn(gatewayResult);

        // Act
        PaymentTransaction result = paymentService.execute(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-100");
        assertThat(result.getTiendaId()).isEqualTo(1L);
        assertThat(result.getOpenpayTransactionId()).isEqualTo("tr-openpay-999");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getAuthorizationCode()).isEqualTo("AUTH-123456");

        verify(paymentRepositoryPort, times(2)).save(any(PaymentTransaction.class));
        verify(paymentGatewayPort, times(1)).createCharge(any(GatewayChargeCommand.class));
    }

    @Test
    @DisplayName("Debe procesar un pago en tienda Paynet generando referencia y código de barras")
    void shouldProcessStorePaymentSuccessfully() {
        // Arrange
        var customer = new CustomerInfo("Elena", "Gómez", "elena@example.com", "5598765432");
        var command = new CreatePaymentCommand(
                "ORD-200",
                1L,
                new BigDecimal("350.00"),
                "MXN",
                PaymentMethod.STORE,
                "Compra en tienda",
                "device-session-456",
                null,
                customer
        );

        when(paymentRepositoryPort.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var storeDetails = PaymentMethodDetails.forStore("101010892812", "https://sandbox-api.openpay.mx/barcode/101010892812");
        var gatewayResult = new GatewayChargeResult(
                "tr-openpay-store-1",
                PaymentStatus.IN_PROGRESS,
                new BigDecimal("350.00"),
                "MXN",
                null,
                storeDetails,
                null
        );
        when(paymentGatewayPort.createCharge(any(GatewayChargeCommand.class))).thenReturn(gatewayResult);

        // Act
        PaymentTransaction result = paymentService.execute(command);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(result.getMethodDetails().reference()).isEqualTo("101010892812");
        assertThat(result.getMethodDetails().barcodeUrl()).contains("101010892812");
    }

    @Test
    @DisplayName("Debe lanzar PaymentNotFoundException al consultar un ID que no existe")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentDoesNotExist() {
        when(paymentRepositoryPort.findById("non-existent-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.execute("non-existent-id"))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("non-existent-id");
    }

    @Test
    @DisplayName("Debe procesar un reembolso parcial exitosamente sobre un pago completado")
    void shouldProcessPartialRefundSuccessfully() {
        // Arrange
        var initialTransaction = new PaymentTransaction(
                "tx-uuid-1",
                "ORD-300",
                1L,
                "tr-openpay-refund-test",
                new BigDecimal("1000.00"),
                "MXN",
                PaymentMethod.CARD,
                PaymentStatus.COMPLETED,
                "Compra general",
                "AUTH-777",
                PaymentMethodDetails.empty(),
                null,
                BigDecimal.ZERO,
                null,
                Instant.now(),
                Instant.now()
        );

        when(paymentRepositoryPort.findById("tx-uuid-1")).thenReturn(Optional.of(initialTransaction));
        when(paymentRepositoryPort.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        var refundCommand = new RefundCommand(new BigDecimal("300.00"), "Devolución parcial de producto");

        // Act
        PaymentTransaction result = paymentService.execute("tx-uuid-1", refundCommand);

        // Assert
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(result.getRefundedAmount()).isEqualByComparingTo(new BigDecimal("300.00"));

        verify(paymentGatewayPort).refundCharge("tr-openpay-refund-test", new BigDecimal("300.00"), "Devolución parcial de producto");
        verify(paymentRepositoryPort).save(initialTransaction);
    }

    @Test
    @DisplayName("Debe lanzar InvalidPaymentOperationException si el monto de reembolso excede el monto original")
    void shouldThrowExceptionWhenRefundExceedsTotalAmount() {
        var initialTransaction = new PaymentTransaction(
                "tx-uuid-2",
                "ORD-301",
                1L,
                "tr-openpay-refund-exceed",
                new BigDecimal("100.00"),
                "MXN",
                PaymentMethod.CARD,
                PaymentStatus.COMPLETED,
                "Compra",
                "AUTH-888",
                PaymentMethodDetails.empty(),
                null,
                BigDecimal.ZERO,
                null,
                Instant.now(),
                Instant.now()
        );

        when(paymentRepositoryPort.findById("tx-uuid-2")).thenReturn(Optional.of(initialTransaction));

        var refundCommand = new RefundCommand(new BigDecimal("150.00"), "Monto mayor al cobrado");

        assertThatThrownBy(() -> paymentService.execute("tx-uuid-2", refundCommand))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("no puede exceder el monto original");

        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    @DisplayName("Debe actualizar el estado a COMPLETED al recibir un webhook charge.succeeded")
    void shouldUpdateStatusToCompletedOnWebhookChargeSucceeded() {
        var initialTransaction = new PaymentTransaction(
                "tx-uuid-webhook",
                "ORD-400",
                1L,
                "tr-openpay-webhook-1",
                new BigDecimal("200.00"),
                "MXN",
                PaymentMethod.STORE,
                PaymentStatus.IN_PROGRESS,
                "Pago pendiente en tienda",
                null,
                PaymentMethodDetails.empty(),
                null,
                BigDecimal.ZERO,
                null,
                Instant.now(),
                Instant.now()
        );

        when(paymentRepositoryPort.findByOpenpayTransactionId("tr-openpay-webhook-1"))
                .thenReturn(Optional.of(initialTransaction));

        var webhook = new WebhookNotification(
                "charge.succeeded",
                "2026-09-08T15:00:00Z",
                "tr-openpay-webhook-1",
                "completed",
                "AUTH-STORE-PAID",
                null
        );

        paymentService.execute(webhook);

        assertThat(initialTransaction.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(initialTransaction.getAuthorizationCode()).isEqualTo("AUTH-STORE-PAID");
        verify(paymentRepositoryPort).save(initialTransaction);
    }
}
