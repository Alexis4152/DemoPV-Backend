package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_cuts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashCut {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // quien abrió el corte (siempre el propio cajero/vendedor)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // quien lo cerró — null mientras sigue abierto, y también null si lo cerró el job
    // automático (CashCutAutoCloseJob) en vez de una persona; el frontend muestra "Sistema".
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal openingAmount = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal closingAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal expenses = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cashSales = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cardSales = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal transferSales = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalTransactions = 0;

    // columnDefinition incluye un DEFAULT para que el ALTER TABLE funcione contra filas ya existentes
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer cancelledCount = 0;

    @Column(nullable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) default 0")
    @Builder.Default
    private BigDecimal cancelledTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private CashCutStatus status = CashCutStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    private LocalDateTime openedAt;

    private LocalDateTime closedAt;
}
