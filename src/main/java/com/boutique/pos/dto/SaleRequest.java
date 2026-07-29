package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SaleRequest {
    private String customerName;
    // opcional: si se captura, se manda el ticket en PDF a este correo
    @Email
    private String customerEmail;
    @NotNull
    private PaymentMethod paymentMethod;
    private BigDecimal discount;
    private BigDecimal tax;
    private String notes;
    @NotEmpty @Valid
    private List<SaleItemRequest> items;
}
