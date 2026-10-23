package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.MaintenanceWindowView;
import com.boutique.pos.service.MaintenanceWindowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único endpoint público (sin sesión, ver {@code SecurityConfig}) sobre ventanas de
 * mantenimiento — lo consulta {@code Login.jsx} (de cualquier tienda, es de plataforma) para
 * mostrar el aviso de mantenimiento programado o en curso. {@code data=null} si no hay
 * ninguna ventana relevante ahora mismo.
 */
@RestController
@RequestMapping("/api/public/maintenance-window")
@RequiredArgsConstructor
public class PublicMaintenanceController {

    private final MaintenanceWindowService maintenanceWindowService;

    @GetMapping
    public ApiResponse<MaintenanceWindowView> current() {
        return ApiResponse.ok(maintenanceWindowService.current().orElse(null));
    }
}
