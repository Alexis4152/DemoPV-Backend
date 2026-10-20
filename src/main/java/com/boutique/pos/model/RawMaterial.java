package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Materia prima de una {@link Tienda}: inventario que NO está a la venta (no aparece en el
 * Punto de Venta, ni en apartados, ni en reportes de ventas). Se maneja por piezas enteras,
 * igual que el inventario, y cada cambio de stock queda en un {@link RawMaterialMovement}.
 * Una parte se puede pasar al inventario vendible (ver {@code RawMaterialService#transferToInventory}),
 * que suma ese número de piezas a un {@link Product}. Se elimina con borrado suave.
 */
@Entity
@Table(name = "raw_materials", uniqueConstraints = @UniqueConstraint(columnNames = {"tienda_id", "barcode"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RawMaterial {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String barcode;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer minStock = 0;

    /** Opcional: usa las mismas categorías de la tienda que el inventario. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Último producto al que se pasó esta materia prima; se sugiere la siguiente vez. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_product_id")
    private Product linkedProduct;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_user_id")
    private User deletedBy;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @JsonProperty("linkedProductId")
    public Long getLinkedProductId() {
        return linkedProduct != null ? linkedProduct.getId() : null;
    }

    @JsonProperty("linkedProductName")
    public String getLinkedProductName() {
        return linkedProduct != null && Boolean.TRUE.equals(linkedProduct.getIsActive()) ? linkedProduct.getName() : null;
    }
}
