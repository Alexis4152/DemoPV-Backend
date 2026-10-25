package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload para que un cajero/admin registre, en persona, un apartado con anticipo
 * ({@code POST /api/apartados/deposit}) — ver {@code ApartadoService#createWithDeposit}.
 * A diferencia de {@link ApartadoRequest} (pública, sin cobro), este SÍ cobra de verdad: el
 * cajero debe tener un corte de caja propio abierto, igual que al registrar cualquier venta.
 */
@Data
public class ApartadoDepositRequest {
    @NotBlank
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String customerName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^[0-9]{10}$", message = "El teléfono debe tener 10 dígitos")
    private String customerPhone;

    @Email
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String customerEmail;

    @Size(max = 200, message = "Las notas no pueden tener más de 200 caracteres")
    private String notes;

    @NotEmpty(message = "Agrega al menos un producto")
    @Valid
    private List<ApartadoItemRequest> items;

    // Cuántas horas dura el apartado antes de vencer; null = usa el default de la tienda
    // (igual que al confirmar un apartado normal — ver ApartadoConfirmRequest).
    private Integer durationHours;

    @NotNull(message = "Captura cuánto dejó de anticipo")
    @DecimalMin(value = "0.01", message = "El anticipo debe ser mayor a 0")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal depositAmount;

    @NotNull
    private PaymentMethod paymentMethod;

    // Con cuánto pagó el cliente el anticipo; obligatorio si paymentMethod = CASH.
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    private BigDecimal amountReceived;
}
