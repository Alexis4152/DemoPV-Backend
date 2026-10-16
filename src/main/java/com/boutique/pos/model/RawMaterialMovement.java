package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro histórico e inmutable de un cambio en el stock de una {@link RawMaterial}, con el
 * stock antes y después. Cuando las piezas pasan al inventario ({@code TO_INVENTORY}) guarda
 * también el {@link Product} que las recibió.
 */
@Entity
@Table(name = "raw_material_movements", indexes = @Index(name = "idx_raw_material_movements_material", columnList = "raw_material_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RawMaterialMovement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private RawMaterialMovementType type;

    /** Piezas movidas (positiva; el efecto lo da {@code type}). */
    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer previousStock;

    @Column(nullable = false)
    private Integer newStock;

    @Column(length = 255)
    private String reason;

    /** Producto que recibió las piezas (solo {@code TO_INVENTORY}). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @JsonProperty("userName")
    public String getUserName() {
        return user != null ? user.getName() : null;
    }

    @JsonProperty("productName")
    public String getProductName() {
        return product != null ? product.getName() : null;
    }
}
