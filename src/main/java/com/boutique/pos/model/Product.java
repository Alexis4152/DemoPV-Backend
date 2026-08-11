package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Producto vendible del catálogo de una {@link Tienda} (dato aislado por tenant).
 * <p>
 * El {@code stock} se actualiza automáticamente con cada venta, cancelación de venta,
 * o movimiento manual, y cada cambio queda registrado como un {@link InventoryMovement}.
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code deletedAt})
 * para conservar el historial de auditoría y no romper la referencia desde ventas pasadas.
 */
@Entity
@Table(name = "products")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Product {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Código de barras del producto, usado para búsqueda rápida en el punto de venta. */
    @Column(length = 100)
    private String barcode;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    /** Umbral mínimo de stock; por debajo de este valor el producto se considera con inventario bajo. */
    @Column(nullable = false)
    @Builder.Default
    private Integer minStock = 0;

    /** Unidad de medida en la que se vende el producto (ej. "pza", "kg", "lt"). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String unit = "pza";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // LAZY: User también carga su Role/Tienda en EAGER, y evitar el ciclo
    // User→Role→createdBy(User)→Role→... es más simple que ir entidad por entidad.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_user_id")
    private User deletedBy;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
