package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payload usado tanto para abrir como para cerrar un corte de caja
 * ({@code POST /api/cash-cuts/open} y {@code POST /api/cash-cuts/{id}/close}).
 * Cada cajero/vendedor puede abrir su propio {@code CashCut} diario con un fondo inicial;
 * varios cortes pueden estar abiertos al mismo tiempo en una misma tienda, uno por usuario.
 * <p>
 * El significado de los campos cambia según la operación: al abrir, {@code amount} es el
 * fondo inicial en caja y {@code expenses} no se usa; al cerrar, {@code amount} se ignora y
 * lo relevante son {@code expenses} (gastos capturados durante el turno) y {@code notes}.
 */
@Data
public class CashCutRequest {
    // Fondo inicial en caja al abrir el corte. Requerido y validado solo en la apertura;
    // en el cierre este valor no se utiliza.
    @NotNull @PositiveOrZero
    private BigDecimal amount;
    // Gastos del turno, capturados únicamente al cerrar el corte manualmente
    // (el cierre automático del sistema siempre los deja en cero).
    @PositiveOrZero
    private BigDecimal expenses;
    private String notes;
}
