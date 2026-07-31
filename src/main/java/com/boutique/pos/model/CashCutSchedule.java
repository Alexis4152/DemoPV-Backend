package com.boutique.pos.model;

import jakarta.persistence.*;
import lombok.*;

// Fila única (id=1) con la hora en que el job de cierre automático corre para
// TODAS las tiendas — es un horario global, no por tienda.
@Entity
@Table(name = "cash_cut_schedule")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashCutSchedule {

    @Id
    private Long id;

    @Column(nullable = false)
    private Integer closeHour;

    @Column(nullable = false)
    private Integer closeMinute;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;
}
