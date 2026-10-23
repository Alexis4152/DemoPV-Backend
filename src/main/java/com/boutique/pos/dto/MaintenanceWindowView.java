package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vista de una ventana de mantenimiento, usada tanto por el panel de SUPER_ADMIN como por
 * el aviso público del Login — no lleva nada sensible (ni auditoría ni datos internos), así
 * que ambos usan exactamente la misma forma.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MaintenanceWindowView {
    private Long id;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    // "SCHEDULED" (todavía no empieza) o "ACTIVE" (ya empezó, no ha terminado) — calculado
    // al vuelo contra la hora del servidor, ver MaintenanceWindowService#toView.
    private String phase;
}
