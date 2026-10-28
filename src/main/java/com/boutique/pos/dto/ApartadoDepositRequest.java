package com.boutique.pos.dto;

import com.boutique.pos.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

/**
 * Payload para que un cajero/admin registre, en persona, un apartado con anticipo
 * ({@code POST /api/apartados/deposit}) — ver {@code ApartadoService#createWithDeposit}.
 * A diferencia de {@link ApartadoRequest} (pública, sin cobro), este SÍ cobra de verdad: el
 * cajero debe tener un corte de caja propio abierto, igual que al registrar cualquier venta.
 */
@Data
public class ApartadoDepositRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String customerName;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^[0-9]{10}$", message = "El teléfono debe tener 10 dígitos")
    private String customerPhone;

    @Email(message = "Correo inválido")
    @Size(max = 150, message = "El correo no puede tener más de 150 caracteres")
    private String customerEmail;

    @Size(max = 200, message = "Las notas no pueden tener más de 200 caracteres")
    private String notes;

    @NotEmpty(message = "Agrega al menos un producto")
    @Valid
    private List<ApartadoItemRequest> items;

    // Fecha de entrega del pedido (reemplaza a las horas de vigencia: un anticipo no vence
    // solo). Debe ser hoy o posterior — ApartadoService#createWithDeposit lo valida.
    @NotNull(message = "Elige la fecha de entrega")
    private LocalDate deliveryDate;

    @NotNull(message = "Captura cuánto dejó de anticipo")
    @DecimalMin(value = "0.01", message = "El anticipo debe ser mayor a 0")
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
    private BigDecimal depositAmount;

    @NotNull(message = "Elige el método de pago")
    private PaymentMethod paymentMethod;

    // Con cuánto pagó el cliente el anticipo; obligatorio si paymentMethod = CASH.
    @DecimalMax(value = "9999999999.99", message = "El número es excesivamente grande — el máximo permitido es 9,999,999,999.99")
    @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales")
    private BigDecimal amountReceived;
}
