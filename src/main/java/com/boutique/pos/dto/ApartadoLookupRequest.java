package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos para que un cliente sin cuenta consulte el estado de un apartado que ya hizo (ver
 * {@code PublicController#lookupApartado}) — el folio (id) por sí solo no basta para verlo,
 * también debe coincidir el teléfono que dejó al solicitarlo (comparación tolerante a
 * formato, ver {@code ApartadoService#phonesMatch}), así alguien no puede enumerar folios
 * ajenos solo probando números consecutivos.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ApartadoLookupRequest {

    @NotNull(message = "El folio es obligatorio")
    private Long id;

    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;
}
