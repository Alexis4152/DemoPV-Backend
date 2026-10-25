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
 * Venta realizada en el punto de venta (POS), asociada al {@link CashCut} abierto del
 * {@link User} que la registró y a los {@link SaleItem} (líneas/productos) que la componen.
 * <p>
 * Una venta nunca se borra: cancelarla ({@code status = }{@link SaleStatus#CANCELLED}) revierte
 * el stock de sus artículos y registra {@code cancelledBy}/{@code cancelledAt}, conservando la
 * fila para el historial y los reportes.
 */
@Entity
@Table(name = "sales", uniqueConstraints = @UniqueConstraint(name = "uk_sales_tienda_folio", columnNames = {"tienda_id", "folio"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Sale {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Folio visible al cliente/cajero (ticket, correo, reportes): consecutivo POR TIENDA,
    // no global — ver Tienda.lastSaleFolio. A diferencia de `id` (la llave real, nunca
    // mostrada), dos tiendas distintas pueden tener cada una su propia venta folio 1.
    @Column(nullable = false)
    private Long folio;

    /** Cajero/vendedor que realizó la venta. */
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Corte de caja del vendedor al que se aplica esta venta. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cash_cut_id")
    private CashCut cashCut;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(length = 150)
    private String customerName;

    @Column(length = 150)
    private String customerEmail;

    /** Suma de los subtotales de los {@link SaleItem}, antes de descuento e impuestos. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    /** Monto final cobrado al cliente: {@code subtotal - discount + tax}. */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    // Solo aplican cuando paymentMethod = CASH (obligatorios en ese caso); null para
    // tarjeta/transferencia, y también null en ventas registradas antes de este feature.
    // changeGiven = amountReceived - total, siempre calculado por el servidor.
    @Column(precision = 12, scale = 2)
    private BigDecimal amountReceived;

    @Column(precision = 12, scale = 2)
    private BigDecimal changeGiven;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private SaleStatus status = SaleStatus.COMPLETED;

    // REGULAR para toda venta de mostrador de siempre; DEPOSIT/BALANCE solo para las que
    // nacen de un apartado con anticipo (ver ApartadoService#createWithDeposit/#complete)
    // — ambas quedan ligadas al mismo apartado vía apartadoId/apartadoFolio.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10) default 'REGULAR'")
    @Builder.Default
    private SaleKind kind = SaleKind.REGULAR;

    /** Id del {@link Apartado} del que nació esta venta (solo si {@code kind != REGULAR}). */
    private Long apartadoId;

    /** Folio (no el id) de ese mismo apartado — lo que de verdad se le muestra al personal. */
    private Long apartadoFolio;

    // Transiente: SOLO se llena en ventas kind=DEPOSIT, con el saldo que todavía falta
    // cobrar del apartado (apartado.total - apartado.depositAmount) — ver
    // SaleService#withPendingBalance, usado por la pantalla de Historial de ventas para
    // mostrar "Anticipo — saldo $X" sin que el frontend tenga que ir a buscar el apartado
    // aparte. No es columna de "sales" (igual que Product#primaryImage).
    private transient BigDecimal pendingBalance;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Líneas (productos y cantidades) que componen la venta. */
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    // quién y cuándo la canceló — null si sigue COMPLETED. LAZY para evitar cargar en
    // cadena el Role/Tienda de ese usuario (mismo motivo que en las demás entidades).
    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id")
    private User cancelledBy;

    private LocalDateTime cancelledAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
