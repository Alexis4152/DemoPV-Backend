package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "¿No tienes tu folio?" — consulta todos los apartados recientes de un teléfono en una
 * tienda, sin necesitar el folio (ver {@code PublicController#lookupApartadosByPhone} y
 * {@code ApartadoService#publicApartadoLookupByPhone}).
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ApartadoPhoneLookupRequest {

    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;
}
