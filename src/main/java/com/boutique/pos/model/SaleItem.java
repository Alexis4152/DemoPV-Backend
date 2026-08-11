package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Línea individual de una {@link Sale}: un producto vendido, con la cantidad y el precio
 * aplicados en el momento de la venta.
 * <p>
 * {@code productName} y {@code unitPrice} son una copia ("fotografía") de los datos del
 * {@link Product} al momento de vender, para que el ticket y el historial de la venta no
 * cambien si posteriormente se edita el nombre o el precio del producto en el catálogo.
 */
@Entity
@Table(name = "sale_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SaleItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonBackReference
    private Sale sale;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Nombre del producto al momento de la venta (copia independiente del catálogo). */
    @Column(nullable = false, length = 200)
    private String productName;

    /** Cantidad vendida (decimal para soportar unidades como kg o lt, no solo piezas enteras). */
    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    /** Precio unitario aplicado al momento de la venta (copia del precio del producto). */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    /** Importe de esta línea: {@code quantity * unitPrice - discount}. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;
}
