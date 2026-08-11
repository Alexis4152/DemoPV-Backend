package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private String customerName;
    // opcional: si se captura, se manda el ticket en PDF a este correo
    @Email
    private String customerEmail;
    @NotNull
    private PaymentMethod paymentMethod;
    // Descuento global de la venta (opcional, se trata como cero si no se envía),
    // adicional a los descuentos por partida en cada SaleItemRequest.
    private BigDecimal discount;
    // Impuesto de la venta (opcional, se trata como cero si no se envía).
    private BigDecimal tax;
    private String notes;
    @NotEmpty @Valid
    private List<SaleItemRequest> items;
}
