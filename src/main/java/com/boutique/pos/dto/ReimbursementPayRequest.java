package com.boutique.pos.dto;

import com.boutique.pos.model.ExternalPaymentMethod;
import com.boutique.pos.model.ReimbursementPaymentSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload de {@code POST /api/reimbursements/{id}/pay}. Con {@code CASH_CUT} el dinero sale
 * de la caja del corte abierto {@code cashCutId}; con {@code EXTERNAL} se paga por fuera de
 * la caja ({@code method} y {@code paidById} obligatorios).
 */
@Data
public class ReimbursementPayRequest {
    @NotNull(message = "Indica de dónde sale el dinero")
    private ReimbursementPaymentSource source;

    private Long cashCutId;

    private ExternalPaymentMethod method;

    private Long paidById;

    @Size(max = 100, message = "La referencia no puede tener más de 100 caracteres")
    private String reference;

    @Size(max = 300)
    private String receiptPath;
}
