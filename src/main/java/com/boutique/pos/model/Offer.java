package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Promoción tipo "compra X paga Y" (2x1, 3x2, 4x2, ...), propiedad de una {@link Tienda}
 * (dato aislado por tenant), aplicable a varios {@link Product} a la vez.
 * <p>
 * La cantidad que captura el cajero en el Punto de Venta es SIEMPRE piezas físicas, nunca
 * "número de promociones" — ver {@code SaleService#applyOffer}, que es quien calcula
 * cuántas promociones completas caben en esa cantidad (y cuánto de "sobrante" queda fuera,
 * a precio normal, igual que si no hubiera oferta). El stock siempre baja por el total de
 * piezas que se lleva el cliente, nunca por lo que se cobra.
 * <p>
 * Se elimina mediante borrado suave ({@code isActive=false} + {@code deletedBy}/{@code
 * deletedAt}), igual que {@link Category}/{@link Product}, para no romper el historial de
 * ventas que ya aplicaron esta oferta (las líneas de venta guardan su propia "fotografía"
 * del nombre/precio, no una referencia viva a la oferta).
 */
@Entity
@Table(name = "offers")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Offer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** Piezas que el cliente se lleva por cada promoción completa (el "X" de "compra X"). */
    @Column(nullable = false)
    private Integer buyQuantity;

    /** Piezas que el cliente paga por cada promoción completa (el "Y" de "paga Y"). */
    @Column(nullable = false)
    private Integer payQuantity;

    // Tope opcional de cuántas promociones completas se aplican EN UNA SOLA línea de
    // venta de un producto con esta oferta — null = sin límite. Sin esto, alguien podría
    // llevarse 50 piezas todas a precio de oferta si el dueño no quería permitir eso; con
    // el límite puesto, lo que exceda se cobra a precio normal (mismo "balde" que el
    // sobrante que no alcanza a formar otra promoción completa).
    private Integer maxPromosPerSale;

    // Ambos nulos = oferta activa indefinidamente hasta que alguien la apague a mano.
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Builder.Default
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    // EAGER: la pantalla de Ofertas siempre necesita saber a qué productos aplica cada
    // una (no hay un listado aparte "productos de esta oferta" que justifique LAZY aquí),
    // y el volumen de ofertas por tienda es bajo. Tabla puente propia (no reutiliza
    // ninguna existente) porque una oferta puede ligarse a muchos productos y un producto
    // puede tener varias ofertas a la vez (aunque en la práctica normalmente sea una sola
    // vigente — eso lo decide SaleService#findActiveOfferForProduct, no esta entidad).
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "offer_products",
            joinColumns = @JoinColumn(name = "offer_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    private List<Product> products = new ArrayList<>();

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

    /**
     * Si esta oferta está vigente ahora mismo: activa, dentro de su rango de fechas (si
     * tiene) — no valida nada de producto ni de tienda, solo su propio estado/vigencia.
     *
     * @param now momento a evaluar (inyectado, no {@code LocalDateTime.now()} interno, para
     *            que quien llama pueda evaluar varias ofertas con el mismo instante exacto)
     */
    public boolean isCurrentlyActive(LocalDateTime now) {
        if (!Boolean.TRUE.equals(isActive)) return false;
        if (startsAt != null && now.isBefore(startsAt)) return false;
        if (endsAt != null && now.isAfter(endsAt)) return false;
        return true;
    }
}
