package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto tal como se muestra en la tienda pública de apartados ({@code
 * PublicController}) — deliberadamente NO es la entidad {@link com.boutique.pos.model.Product}
 * completa: sin costo, sin auditoría, sin categoría anidada, y CON sus fotos (que
 * {@code Product} no carga por sí solo, para no cargarlas de más en el resto de la app
 * donde no hacen falta — ver {@code ApartadoService#toPublicDto}).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicProductDto {
    private Long id;
    private String name;
    private String description;
    /** Precio de lista, sin descuento. */
    private BigDecimal price;
    /** Porcentaje de descuento promocional de apartado, o null si no tiene oferta. */
    private BigDecimal discountPercent;
    /** {@code price} ya con el descuento aplicado — igual a {@code price} si no hay oferta. Es lo que paga el cliente. */
    private BigDecimal finalPrice;
    private String unit;
    private Integer stock;
    /** Rutas públicas de las fotos ({@code /uploads/...}), portada primero; vacío si no tiene ninguna. */
    private List<String> images;
}
