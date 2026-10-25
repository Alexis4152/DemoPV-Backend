package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.InventoryActivityDto;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.service.InventoryActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pestaña "Movimientos" de Inventario (ver {@link InventoryActivityService}). Además de
 * acceso a Inventario, el servicio exige ser admin/supervisor/super admin.
 */
@RestController
@RequestMapping("/api/inventory-activity")
@RequiredArgsConstructor
public class InventoryActivityController {

    private final InventoryActivityService activityService;

    @GetMapping
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<PageResponse<InventoryActivityDto>>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(
                activityService.search(actor, from, to, q, categoryId, userId, action, sort, dir, page, size)));
    }

    /** Usuarios con alguna acción en el rango, para el filtro "Usuario". */
    @GetMapping("/users")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> users(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(activityService.users(actor, from, to)));
    }
}
