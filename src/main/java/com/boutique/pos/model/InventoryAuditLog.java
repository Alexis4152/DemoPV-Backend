package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Bitácora de Inventario: quién hizo qué acción que no mueve stock (alta, edición, baja,
 * carga masiva, descargas, códigos, etiquetas) y cuándo. Junto con {@link
 * InventoryMovement} forma la pestaña "Movimientos" de Inventario.
 */
@Entity
@Table(name = "inventory_audit_logs", indexes = @Index(name = "idx_inv_audit_tienda_created", columnList = "tienda_id, created_at"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tienda_id", nullable = false)
    private Long tiendaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryAuditAction action;

    // Producto afectado, si la acción es sobre uno solo (alta, edición, baja, etiqueta).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
