package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Línea individual de un {@link Apartado}: un producto reservado, con la cantidad y el
 * precio vigentes al momento de crear la solicitud. Igual que {@link SaleItem}, guarda
 * {@code productName}/{@code unitPrice} como "fotografía" del producto en ese momento,
 * para que el historial del apartado no cambie si después se edita el catálogo.
 */
@Entity
@Table(name = "apartado_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ApartadoItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apartado_id", nullable = false)
    @JsonBackReference
    private Apartado apartado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 200)
    private String productName;

    // BigDecimal, no Integer: igual que SaleItem.quantity, para soportar unidades como kg
    // o lt (no solo piezas enteras) y para poder mapear 1 a 1 al convertir el apartado en
    // una Sale al completarse.
    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Descuento por línea, capturado por el cajero al confirmar (nunca por el cliente
     *  público) — validado contra {@link Tienda#getMaxApartadoDiscountAmount()}/
     *  {@link Tienda#getMaxApartadoDiscountPercent()}. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    /** {@code unitPrice * quantity - discount}. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;
}
