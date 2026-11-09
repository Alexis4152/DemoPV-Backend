package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "apartados", uniqueConstraints = @UniqueConstraint(name = "uk_apartados_tienda_folio", columnNames = {"tienda_id", "folio"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Apartado {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Folio visible al cliente (vitrina pública, correos): consecutivo POR TIENDA, no
    // global — ver Tienda.lastApartadoFolio. A diferencia de `id`, dos tiendas distintas
    // pueden tener cada una su propio apartado folio 1.
    @Column(nullable = false)
    private Long folio;

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

    // ONLINE para todo lo de siempre (solicitado desde la tienda pública); DEPOSIT para
    // los que un cajero registra en persona al recibir un anticipo — ver
    // ApartadoService#createWithDeposit. Default ONLINE para que los apartados ya
    // existentes en la base (de antes de este campo) sigan contando como lo que siempre
    // fueron.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'ONLINE'")
    @Builder.Default
    private ApartadoOrigin origin = ApartadoOrigin.ONLINE;

    /** Cuánto dejó el cliente de anticipo (solo si {@code origin == DEPOSIT}); null en los demás. */
    @Column(precision = 12, scale = 2)
    // Lo pagado a cuenta: el anticipo más los abonos (ver ApartadoService#addInstallment);
    // el saldo es total - depositAmount.
    private BigDecimal depositAmount;

    /** Id de la {@link Sale} kind=DEPOSIT generada al recibir el anticipo. Null hasta entonces. */
    private Long depositSaleId;

    /** Folio de esa misma venta de anticipo. Null hasta entonces. */
    private Long depositSaleFolio;

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

    /** {@code confirmedAt + durationHours}. Null mientras está {@code PENDING} (todavía no corre el conteo).
     *  También null en los anticipos ({@code origin = DEPOSIT}): no vencen solos — el cliente ya
     *  dejó dinero; quedan activos hasta que se entregan o alguien los cancela. */
    private LocalDateTime expiresAt;

    /** Fecha en que se le entrega el pedido al cliente (solo anticipos). Un día antes el
     *  apartado se marca "Próximo a entregar" en la pantalla de Apartados. */
    private LocalDate deliveryDate;

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

    /** Al cancelar un anticipo: si se le devolvió al cliente o se lo quedó la tienda. Null
     *  en los demás apartados y mientras no se cancele. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DepositResolution depositResolution;

    /** % del total que la tienda retiene si el cliente cancela o devuelve por cambio de
     *  opinión, copiado de la tienda al registrar el anticipo (lo que dice su ticket). Null en
     *  los apartados de antes: lo pagado a cuenta no se devuelve. */
    @Column(precision = 5, scale = 2)
    private BigDecimal retentionPercent;

    /** Al cancelar o devolver un apartado con anticipo: por qué (cambio de opinión o
     *  defectuoso) y cuánto se quedó la tienda de lo pagado. */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ApartadoReturnReason returnReason;

    @Column(precision = 12, scale = 2)
    private BigDecimal retainedAmount;

    // Devolución de un apartado ya completado (el cliente lo recogió y lo regresó, ver
    // ApartadoService#refund). El estado sigue COMPLETED: sí se completó; esto marca que
    // después se devolvió.
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by_user_id")
    private User refundedBy;

    private LocalDateTime refundedAt;

    @Column(length = 300)
    private String refundReason;

    // Venció o lo canceló el cliente desde la tienda en línea: el producto NO regresó solo al
    // inventario. Queda pendiente hasta que alguien de la tienda revise qué pasó con cada
    // producto (ver ApartadoService#reviewStock). Mientras tanto sigue fuera del stock.
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean stockReviewPending = false;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_reviewed_by_user_id")
    private User stockReviewedBy;

    private LocalDateTime stockReviewedAt;

    // Pagado completo en el primer pago (ver ApartadoService#createWithDeposit): ya se cobró
    // todo, pero el cliente recoge después. Sigue COMPLETED y "por entregar" hasta que alguien
    // registre la entrega (ApartadoService#deliver); ahí se decide qué sale del inventario de
    // lo sobre pedido, igual que al liquidar.
    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean pendingDelivery = false;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by_user_id")
    private User deliveredBy;

    private LocalDateTime deliveredAt;

    /** Quién registró la entrega, para la subtabla de productos (deliveredBy es LAZY y no viaja). */
    @JsonProperty("deliveredByName")
    public String getDeliveredByName() {
        return deliveredBy != null ? deliveredBy.getName() : null;
    }

    /**
     * Cuándo se decidió qué pasó con los productos (regresó, merma, no regresó): al revisar el
     * inventario (vencido o cancelado por el cliente), al devolver o al cancelar en tienda.
     */
    @JsonProperty("outcomeAt")
    public LocalDateTime getOutcomeAt() {
        return stockReviewedAt != null ? stockReviewedAt : refundedAt != null ? refundedAt : cancelledAt;
    }

    /** Quién lo decidió (ver {@link #getOutcomeAt()}); null si fue automático. */
    @JsonProperty("outcomeByName")
    public String getOutcomeByName() {
        User by = stockReviewedAt != null ? stockReviewedBy : refundedAt != null ? refundedBy : cancelledBy;
        return by != null ? by.getName() : null;
    }

    /** Id de la {@link Sale} generada al completarse (recoger y pagar). Null hasta entonces. */
    private Long saleId;

    /** Folio (no el id) de esa misma {@link Sale} — lo que de verdad se le muestra al
     *  personal como "Venta #", en vez del id interno. Null hasta entonces. */
    private Long saleFolio;

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
