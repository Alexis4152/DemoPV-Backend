package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos de una tienda expuestos en su vitrina pública de apartados — a propósito NO es la
 * entidad {@link com.boutique.pos.model.Tienda} completa: nunca deben salir por esta vía
 * sus límites de descuento, ids de auditoría, ni ningún otro dato interno.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicTiendaDto {
    private String name;
    private String logoPath;
    private String primaryColor;

    /** {@code Tienda#getDefaultApartadoHours()} — horas que dura un apartado ya
     *  confirmado, para que el cliente sepa de entrada cuánto plazo tendría para
     *  recogerlo y pagarlo si la tienda lo confirma. No es un dato sensible. */
    private Integer defaultApartadoHours;
}
