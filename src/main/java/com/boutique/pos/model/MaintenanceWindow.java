package com.boutique.pos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Ventana de mantenimiento programada por SUPER_ADMIN, visible en el Login de TODAS las
 * tiendas (no es algo por tienda, es de plataforma) — ver {@code PublicMaintenanceController}.
 * <p>
 * No hay un campo de "estado" guardado: la fase (programada vs. en curso) y si ya pasó se
 * calculan siempre comparando {@link #startAt}/{@link #endAt} contra la hora actual (ver
 * {@code MaintenanceWindowService#toView}) — así nunca puede quedar desincronizada de la
 * hora real ni depende de un job que la actualice.
 */
@Entity
@Table(name = "maintenance_windows")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MaintenanceWindow {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @JsonIgnoreProperties({"role", "tienda", "createdBy", "updatedBy", "deletedBy"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
