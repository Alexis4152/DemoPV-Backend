package com.boutique.pos.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** Payload para programar una ventana de mantenimiento nueva ({@code POST /api/admin/maintenance-windows}). */
@Data
public class MaintenanceWindowCreate {
    @NotNull
    @Future(message = "La fecha/hora de inicio debe ser en el futuro")
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;
}
