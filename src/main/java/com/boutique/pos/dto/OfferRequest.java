package com.boutique.pos.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload para crear o actualizar una {@code Offer} ({@code POST}/{@code PUT /api/offers/**},
 * restringido a ADMIN/SUPERVISOR/SUPER_ADMIN). La oferta creada queda asociada
 * automáticamente a la tienda del actor.
 */
@Data
public class OfferRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede tener más de 150 caracteres")
    private String name;

    // Topes de 1000: una promoción de más piezas no es realista y, sin tope, un número
    // gigante desborda el INTEGER de la columna con un error crudo de la base de datos.
    @NotNull(message = "Indica cuántas piezas se lleva el cliente (el \"X\" de compra X paga Y)")
    @Min(value = 2, message = "Debe llevarse al menos 2 piezas (si no, no hay oferta que aplicar)")
    @Max(value = 1000, message = "Máximo 1000 piezas por promoción")
    private Integer buyQuantity;

    @NotNull(message = "Indica cuántas piezas paga el cliente (el \"Y\" de compra X paga Y)")
    @Min(value = 1, message = "Debe pagar al menos 1 pieza")
    @Max(value = 999, message = "Máximo 999 piezas pagadas por promoción")
    private Integer payQuantity;

    // null = sin límite de promociones por línea de venta.
    @Min(value = 1, message = "El límite de promociones debe ser al menos 1 (o déjalo vacío para no poner límite)")
    @Max(value = 1000, message = "El límite de promociones no puede ser mayor a 1000")
    private Integer maxPromosPerSale;

    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    private Boolean isActive;

    @NotEmpty(message = "Elige al menos un producto para esta oferta")
    private List<Long> productIds;

    /**
     * Valida que lo que se paga sea realmente menos que lo que se lleva — si no, no es una
     * oferta, es vender normal (o peor, cobrar de más). Se valida aquí, a nivel de DTO, en
     * vez de con una anotación de Bean Validation cruzando dos campos (más simple así que
     * armar un validador a la medida para un solo caso).
     */
    public boolean isPayLessThanBuy() {
        return buyQuantity == null || payQuantity == null || payQuantity < buyQuantity;
    }
}
