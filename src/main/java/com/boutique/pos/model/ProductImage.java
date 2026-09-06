package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Una foto de un {@link Product}, guardada en disco (mismo patrón que {@code
 * TiendaLogoService} para el logo de tienda) y servida vía {@code /uploads/**}. Un
 * producto puede tener varias — pensado sobre todo para exhibirlo en la tienda pública de
 * apartados ({@code isReservable=true}), aunque no es exclusivo de ese flujo.
 * <p>
 * {@code isPrimary} marca cuál es la foto de portada (la que aparece en la cuadrícula del
 * catálogo público); las demás son la galería del detalle del producto. Se ordenan por
 * {@code sortOrder} para que el vendedor controle el orden de la galería.
 */
@Entity
@Table(name = "product_images")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductImage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Ruta pública de la imagen (ej. "/uploads/products/12/xxx.jpg"). */
    @Column(nullable = false, length = 255)
    private String path;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
