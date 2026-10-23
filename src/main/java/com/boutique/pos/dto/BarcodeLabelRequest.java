package com.boutique.pos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * Payload de {@code POST /api/products/barcode-labels}: qué etiquetas de código de barras
 * imprimir (ver {@code BarcodeLabelPdfService}).
 */
@Data
public class BarcodeLabelRequest {
    // Productos a imprimir; vacío o null = todos los productos activos con código de la tienda.
    private List<Long> productIds;

    @Min(value = 1, message = "Imprime al menos 1 etiqueta por producto")
    @Max(value = 500, message = "Máximo 500 etiquetas por producto")
    private Integer copies = 1;

    // "50x25", "51x25", "50x30", "40x30" o "60x40" (mm); cualquier otro valor usa 50x25.
    private String size;
}
