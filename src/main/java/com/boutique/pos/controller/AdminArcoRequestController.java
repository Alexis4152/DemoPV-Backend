package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ArcoRequestResolve;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.model.ArcoRequestStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ArcoRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administra las solicitudes ARCO que llegan desde el formulario público, expuesto bajo
 * {@code /api/admin/arco-requests}. Exclusivo de SUPER_ADMIN — igual que la configuración
 * de correo ({@code AdminMailConfigController}), es infraestructura/obligación legal de la
 * plataforma completa, no algo por tienda que un ADMIN o SUPERVISOR puedan atender.
 */
@RestController
@RequestMapping("/api/admin/arco-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminArcoRequestController {

    private final ArcoRequestService arcoRequestService;

    /** Lista las solicitudes (pendientes primero), opcionalmente acotadas a un estado. */
    @GetMapping
    public ApiResponse<List<ArcoRequestView>> list(@RequestParam(required = false) ArcoRequestStatus status) {
        return ApiResponse.ok(arcoRequestService.list(status), null);
    }

    /** Cuántas solicitudes siguen sin atender — para el badge del menú lateral. */
    @GetMapping("/pending-count")
    public ApiResponse<Long> pendingCount() {
        return ApiResponse.ok(arcoRequestService.countPending(), null);
    }

    @PutMapping("/{id}/resolve")
    public ApiResponse<ArcoRequestView> resolve(@PathVariable Long id, @Valid @RequestBody ArcoRequestResolve request,
                                                 @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.resolve(id, request, actor), "Solicitud actualizada");
    }
}
