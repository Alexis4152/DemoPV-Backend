package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Sale {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;
}
