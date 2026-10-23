package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.MaintenanceWindowCreate;
import com.boutique.pos.dto.MaintenanceWindowExtend;
import com.boutique.pos.dto.MaintenanceWindowView;
import com.boutique.pos.model.User;
import com.boutique.pos.service.MaintenanceWindowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administra las ventanas de mantenimiento de la plataforma, expuesto bajo
 * {@code /api/admin/maintenance-windows}. Exclusivo de SUPER_ADMIN — es infraestructura de
 * toda la plataforma (el aviso sale en el Login de TODAS las tiendas), no algo por tienda.
 * El lado público (lo que lee el Login, sin sesión) vive en {@code PublicMaintenanceController}.
 */
@RestController
@RequestMapping("/api/admin/maintenance-windows")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMaintenanceWindowController {

    private final MaintenanceWindowService maintenanceWindowService;

    @GetMapping("/current")
    public ApiResponse<MaintenanceWindowView> current() {
        return ApiResponse.ok(maintenanceWindowService.current().orElse(null));
    }

    @PostMapping
    public ApiResponse<MaintenanceWindowView> schedule(@Valid @RequestBody MaintenanceWindowCreate req,
                                                         @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(maintenanceWindowService.schedule(req, actor), "Ventana de mantenimiento programada");
    }

    @PutMapping("/{id}/extend")
    public ApiResponse<MaintenanceWindowView> extend(@PathVariable Long id,
                                                       @Valid @RequestBody MaintenanceWindowExtend req,
                                                       @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(maintenanceWindowService.extend(id, req, actor), "Ventana de mantenimiento prolongada");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        maintenanceWindowService.cancel(id, actor);
        return ApiResponse.ok(null, "Ventana de mantenimiento cancelada");
    }
}
