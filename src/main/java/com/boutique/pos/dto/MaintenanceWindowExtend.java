package com.boutique.pos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** Payload para prolongar una ventana de mantenimiento YA EN CURSO ({@code PUT /api/admin/maintenance-windows/{id}/extend}). */
@Data
public class MaintenanceWindowExtend {
    @NotNull
    private LocalDateTime endAt;
}
