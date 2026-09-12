package com.boutique.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload para que un cajero/admin CONFIRME un apartado {@code PENDING} (pasa a {@code
 * ACTIVE} y ahí sí se descuenta el stock — ver {@code ApartadoService#confirm}).
 * <p>
 * Es el único momento en el que se puede aplicar descuento por línea (nunca lo captura el
 * cliente público), validado contra {@code Tienda.maxApartadoDiscountAmount/Percent}. Si
 * no se manda {@code durationHours}, se usa el default de la tienda
 * ({@code Tienda.defaultApartadoHours}).
 */
@Data
public class ApartadoConfirmRequest {
    // Horas que durará la reserva a partir de ahora; null = usar el default de la tienda.
    // Máximo de 8760 (1 año) a propósito: sin tope, un valor absurdo (ej. escrito de más)
    // generaba una fecha de vencimiento sin sentido en vez de un mensaje claro.
    @Min(value = 1, message = "Las horas de vigencia deben ser al menos 1")
    @Max(value = 8760, message = "Las horas de vigencia no pueden ser mayores a 8,760 (1 año)")
    private Integer durationHours;

    @Valid
    private List<ApartadoConfirmItemRequest> items;

    @Data
    public static class ApartadoConfirmItemRequest {
        private Long itemId;
        // El tope real (contra Tienda.maxApartadoDiscountAmount/Percent) lo valida
        // ApartadoService#confirm — esto solo evita un valor negativo sin sentido.
        @PositiveOrZero(message = "El descuento no puede ser negativo")
        private BigDecimal discount;
    }
}
