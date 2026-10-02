package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Confirmación que se le muestra al cliente tras solicitar un apartado, y también lo que
 * ve al consultarlo después por folio + teléfono (ver {@code
 * ApartadoService#publicApartadoLookup}) — a propósito NO es la entidad {@link
 * com.boutique.pos.model.Apartado} completa: esta sí viajaría con la {@link
 * com.boutique.pos.model.Tienda} anidada (límites de descuento, slug, usuarios internos) y
 * cada {@link com.boutique.pos.model.Product} con su costo y auditoría, nada de lo cual
 * debe salir por una respuesta pública sin autenticación.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PublicApartadoDto {
    private Long id;
    private String status;
    private LocalDateTime requestedAt;
    /** Cuándo se confirmó (pasó de PENDING a ACTIVE) — null mientras sigue pendiente. */
    private LocalDateTime confirmedAt;
    /** Plazo para recogerlo, {@code confirmedAt + durationHours} — null mientras sigue pendiente. */
    private LocalDateTime expiresAt;
    /** Cuándo se canceló — null si nunca se canceló. */
    private LocalDateTime cancelledAt;
    /** Motivo de cancelación que el cajero/admin escribió, si dejó alguno. */
    private String cancelReason;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private List<Item> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        /** Id del producto — null si el producto ya se eliminó desde entonces (ver {@code
         *  ApartadoItem#getProduct()}). No es un dato sensible (ya es público en el
         *  catálogo); se expone para que el frontend pueda reconstruir un carrito a partir
         *  de estas líneas al "editar" (cancelar + volver a apartar con lo mismo
         *  precargado, revalidado contra el catálogo actual — mismo mecanismo que ya usa
         *  para restaurar el carrito desde localStorage, ver getPublicProductsByIds). */
        private Long productId;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
