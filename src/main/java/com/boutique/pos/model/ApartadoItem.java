package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // NO es una columna de "apartado_items" — igual que Product.primaryImage, es
    // "transient" de Java (no @jakarta.persistence.Transient, que jackson-datatype-hibernate6
    // ocultaría del JSON) para que sí viaje al frontend sin persistirse. Piezas del
    // producto que de verdad quedan libres para confirmar ESTE apartado: su stock actual
    // menos lo que ya reclaman OTRAS solicitudes PENDING del mismo producto (ver
    // ApartadoService#populateAvailableStock). Solo se llena al consultar un apartado por
    // id mientras sigue PENDING; en cualquier otra respuesta (ej. el listado) queda null.
    private transient Integer availableStock;

    /** Qué pasó con el producto al cancelar el apartado (ver {@link ItemReturnOutcome}). Null
     *  mientras siga vigente, o si nunca se descontó stock (pendiente o por pedido). */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ItemReturnOutcome returnOutcome;

    /** Sobre pedido: la línea no se descontó del inventario, así que tampoco regresa a él. Se
     *  apaga si al liquidar sale del inventario (ver ApartadoService#deductOnOrderItems). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean onOrder = false;

    /**
     * Cuándo se entregó esta línea al cliente (apartado pagado completo, ver
     * ApartadoService#deliver). Se entrega por renglón y en una o varias visitas; null mientras
     * no se entrega. Se conserva aunque después se devuelva el apartado (historial).
     */
    private LocalDateTime deliveredAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by_user_id")
    private User deliveredBy;

    /** Quién entregó la línea (deliveredBy es LAZY y no viaja al JSON). */
    @JsonProperty("deliveredByName")
    public String getDeliveredByName() {
        return deliveredBy != null ? deliveredBy.getName() : null;
    }
}
