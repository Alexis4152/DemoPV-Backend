package com.boutique.pos.dto;

import jakarta.validation.Valid;
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
    /** Horas que durará la reserva a partir de ahora; null = usar el default de la tienda. */
    private Integer durationHours;

    @Valid
    private List<ApartadoConfirmItemRequest> items;

    @Data
    public static class ApartadoConfirmItemRequest {
        private Long itemId;
        private BigDecimal discount;
    }
}
