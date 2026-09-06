package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Confirmación que se le muestra al cliente tras solicitar un apartado — a propósito NO es
 * la entidad {@link com.boutique.pos.model.Apartado} completa: esta sí viajaría con la
 * {@link com.boutique.pos.model.Tienda} anidada (límites de descuento, slug, usuarios
 * internos) y cada {@link com.boutique.pos.model.Product} con su costo y auditoría, nada
 * de lo cual debe salir por una respuesta pública sin autenticación.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicApartadoDto {
    private Long id;
    private String status;
    private LocalDateTime requestedAt;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private List<Item> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
