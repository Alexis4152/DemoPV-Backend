package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * El cliente cancela su propio apartado desde la vitrina pública, sin hablarle a la tienda
 * (ver {@code PublicController#selfCancelApartado} y {@code
 * ApartadoService#publicApartadoCancel}). Mismo folio+teléfono que {@link
 * ApartadoLookupRequest} para verificar que de verdad es suyo — el folio por sí solo no
 * basta, el servidor revalida todo desde cero, nunca confía en que el frontend ya lo
 * verificó al consultarlo.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ApartadoSelfCancelRequest {

    @NotNull(message = "El folio es obligatorio")
    private Long id;

    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;

    /** Motivo opcional que el cliente quiera dejar — mismo campo/límite que cuando cancela un cajero/admin. */
    @Size(max = 500, message = "El motivo no puede tener más de 500 caracteres")
    private String reason;
}
