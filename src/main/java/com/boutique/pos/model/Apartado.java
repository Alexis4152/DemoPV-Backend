package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Apartado (reserva/"layaway") de uno o más productos, hecho por un cliente desde la
 * tienda pública de la tienda ({@code /apartar/{slug}}, sin necesidad de cuenta ni login
 * — ver {@code PublicController}) y gestionado después por el cajero/admin de esa tienda.
 * <p>
 * A diferencia de una {@link Sale}, un apartado NO es una venta: es una promesa de compra
 * con el producto reservado. El stock se descuenta hasta que un cajero lo <b>confirma</b>
 * (pasa de {@code PENDING} a {@code ACTIVE}) — nunca al momento de la solicitud pública,
 * para que un apartado falso o de broma (no hay login ni pago que filtre) no bloquee
 * inventario sin que nadie lo revise. Al completarse (el cliente recoge y paga), se genera
 * una {@link Sale} real ({@link #saleId}) SIN volver a descontar stock, ya que se descontó
 * al confirmar. Si se cancela estando {@code ACTIVE}, o si el job de vencimiento
 * ({@code ApartadoExpiryJob}) lo encuentra vencido, el stock se restituye.
 * <p>
 * Los descuentos aplicables aquí usan un límite SEPARADO del de venta física
 * ({@link Tienda#getMaxApartadoDiscountAmount()}/{@link Tienda#getMaxApartadoDiscountPercent()}),
 * ya que una tienda puede querer incentivar los apartados con condiciones distintas a las
 * de mostrador.
 */
@Entity
@Table(name = "apartados")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Apartado {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    // Datos de contacto del cliente que apartó — sin cuenta ni login, igual que
    // Sale.customerName/customerEmail. El teléfono es el medio de contacto más confiable
    // para que el cajero avise que ya puede recogerlo.
    @Column(nullable = false, length = 150)
    private String customerName;

    @Column(length = 30)
    private String customerPhone;

    @Column(length = 150)
    private String customerEmail;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private ApartadoStatus status = ApartadoStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    /** Horas que dura la reserva a partir de {@link #confirmedAt}; capturado al confirmar
     *  (puede venir del default de la tienda o de un valor que el cajero decida en ese momento). */
    private Integer durationHours;

    /** Cuándo se creó la SOLICITUD (estado {@code PENDING}), sin importar si luego se confirma. */
    @CreationTimestamp
    private LocalDateTime requestedAt;

    /** Cuándo un cajero/admin la confirmó (pasó a {@code ACTIVE} y se descontó el stock). Null mientras está {@code PENDING}. */
    private LocalDateTime confirmedAt;

    /** {@code confirmedAt + durationHours}. Null mientras está {@code PENDING} (todavía no corre el conteo). */
    private LocalDateTime expiresAt;

    // LAZY + JsonIgnoreProperties: mismo motivo que en Sale.user (evitar el ciclo
    // User→Role/Tienda→...→User al serializar, y no cargar en cadena lo que no hace falta).
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private User confirmedBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_user_id")
    private User completedBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    private LocalDateTime cancelledAt;

    /** Motivo que el cajero/admin escribió al cancelar, opcional — si el cliente dejó
     *  correo al solicitar el apartado, este texto es lo que se le manda explicándole por
     *  qué no se pudo concretar (ver {@code EmailService#sendApartadoCancelledEmail}). */
    @Column(columnDefinition = "TEXT")
    private String cancelReason;

    /** Id de la {@link Sale} generada al completarse (recoger y pagar). Null hasta entonces. */
    private Long saleId;

    /**
     * Líneas (productos y cantidades) que componen el apartado. {@code orphanRemoval}
     * porque {@link com.boutique.pos.service.ApartadoService#removeItem} quita una línea
     * directamente de esta colección (ej. el producto se agotó mientras seguía {@code
     * PENDING}) y espera que eso borre la fila de {@code apartado_items}, no solo que la
     * desvincule.
     */
    @OneToMany(mappedBy = "apartado", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<ApartadoItem> items = new ArrayList<>();
}
