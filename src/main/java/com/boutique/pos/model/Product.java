package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
// La unicidad del código de barras es POR TIENDA, no global: dos tiendas distintas
// pueden vender legítimamente el mismo producto de fábrica con el mismo código real.
// Postgres permite múltiples NULL en una columna de un UNIQUE (no todo producto tiene
// barcode), así que esto no afecta a los productos sin código.
@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = {"tienda_id", "barcode"}))
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

    // El ADMIN decide, producto por producto, cuáles salen en la tienda pública de
    // apartados (ver PublicController) — apagado por default: no todo lo que hay en
    // inventario tiene por qué exhibirse públicamente.
    @Column(nullable = false)
    @Builder.Default
    private Boolean isReservable = false;

    // Descuento promocional PÚBLICO para apartados: a diferencia del límite de
    // Tienda.maxApartadoDiscountAmount/Percent (un tope que el cajero aplica en privado al
    // confirmar, invisible para el cliente), este SÍ se le muestra al cliente en la tienda
    // pública como una oferta (precio tachado + precio con descuento) — ver
    // PublicProductDto/ApartadoService. Null o 0 = sin oferta. Validado contra el mismo
    // límite de la tienda al guardar el producto (ver ProductService), para no poder
    // saltarse el tope fijado en "Datos de la tienda" por esta otra vía.
    @Column(precision = 5, scale = 2)
    private BigDecimal apartadoDiscountPercent;

    // LAZY: User también carga su Role/Tienda en EAGER, y evitar el ciclo
    // User→Role→createdBy(User)→Role→... es más simple que ir entidad por entidad.
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

    // NO es una columna de "products" — las fotos viven aparte en product_images (ver
    // ProductImage), porque un producto puede tener varias. Este campo transitorio solo
    // se llena bajo demanda, en bloque para toda una página de resultados (nunca un query
    // por producto), cuando el caller sí necesita mostrar la portada de cada uno — ver
    // ProductService#withPrimaryImages, usado por el buscador del Punto de Venta para su
    // vista "con imágenes". Queda en null en cualquier otra respuesta que no lo llene.
    //
    // OJO: es "transient" de Java (la palabra clave), NO @jakarta.persistence.Transient.
    // Con la anotación, jackson-datatype-hibernate6 (ver pom.xml) OCULTA el campo del JSON
    // por completo aunque tenga valor — trata cualquier @Transient de JPA como "no
    // serializable salvo que se pida explícito", pensado para evitar disparar la carga de
    // un proxy perezoso al serializar. La palabra clave logra lo mismo para Hibernate
    // (JPA también la reconoce como "no persistir esto") sin que ese módulo la intercepte.
    private transient String primaryImage;
}
