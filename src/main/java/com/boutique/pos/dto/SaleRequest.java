package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload para registrar una venta ({@code POST /api/sales}, requiere acceso a la sección
 * POS). La venta se asocia automáticamente al corte de caja abierto del vendedor y a su
 * usuario; el servicio calcula los totales a partir de {@link #getItems()} y descuenta el
 * stock de cada producto vendido. Para cancelar una venta existe un endpoint aparte
 * ({@code DELETE /api/sales/{id}}, solo ADMIN) que revierte el stock — este DTO no se usa
 * para cancelaciones.
 */
@Data
public class SaleRequest {
    // Máximo alineado a sales.customer_name VARCHAR(150). Siempre opcional — se puede
    // vender sin capturar nombre del cliente.
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String customerName;
    // Opcional: si se captura, se manda el ticket en PDF a este correo. Máximo alineado a
    // sales.customer_email VARCHAR(150).
    @Email
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String customerEmail;
    @NotNull
    private PaymentMethod paymentMethod;
    // Descuento global de la venta (opcional, se trata como cero si no se envía),
    // adicional a los descuentos por partida en cada SaleItemRequest.
    private BigDecimal discount;
    // Impuesto de la venta (opcional, se trata como cero si no se envía).
    private BigDecimal tax;
    // Con cuánto pagó el cliente. Obligatorio cuando paymentMethod = CASH (el servicio
    // rechaza la venta si falta o es menor al total); se ignora para tarjeta/transferencia.
    // Máximo alineado a sales.amount_received NUMERIC(12,2) — sin este tope, un monto
    // absurdamente grande pasaba hasta el backend y tronaba con un error crudo de la BD.
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal amountReceived;
    private String notes;
    @NotEmpty @Valid
    private List<SaleItemRequest> items;
}
