package com.boutique.pos.controller;

import com.boutique.pos.dto.ApartadoCancelRequest;
import com.boutique.pos.dto.ApartadoCompleteRequest;
import com.boutique.pos.dto.ApartadoConfirmRequest;
import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ApartadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Controlador de apartados, expuesto bajo {@code /api/apartados} — la parte AUTENTICADA
 * del flujo (revisar, confirmar, completar, cancelar). La creación pública vive aparte,
 * en {@code PublicController} ({@code /api/public/**}, sin autenticación).
 * <p>
 * Todos los endpoints requieren la sección {@code APARTADOS}.
 */
@RestController
@RequestMapping("/api/apartados")
@RequiredArgsConstructor
public class ApartadoController {

    private final ApartadoService apartadoService;

    /**
     * Lista paginada de apartados de la tienda del actor, con filtros combinables por
     * estado, rango de fechas de solicitud y texto libre (cliente o producto).
     *
     * @param status filtro por estado, opcional
     * @param from   fecha mínima de solicitud (inclusiva), opcional
     * @param to     fecha máxima de solicitud (inclusiva), opcional
     * @param q      texto de búsqueda opcional — nombre/teléfono del cliente, o nombre de
     *               algún producto en sus líneas (ver {@link ApartadoService#search})
     */
    @GetMapping
    @PreAuthorize("@sectionAccess.check('APARTADOS')")
    public ResponseEntity<ApiResponse<PageResponse<Apartado>>> list(
            @RequestParam(required = false) ApartadoStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Apartado> result = apartadoService.search(status, from, to, q, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Cuántos apartados {@code PENDING} tiene la tienda del actor — para el badge del
     * sidebar. Accesible también desde {@code DASHBOARD}, igual que el resumen de ventas.
     */
    @GetMapping("/pending-count")
    @PreAuthorize("@sectionAccess.checkAny('APARTADOS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<Long>> pendingCount(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.pendingCount(actor), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('APARTADOS')")
    public ResponseEntity<ApiResponse<Apartado>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.findById(id, actor), null));
    }

    /**
     * Confirma un apartado {@code PENDING}: descuenta el stock y arranca el plazo de
     * vigencia (ver {@link ApartadoService#confirm}).
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@sectionAccess.check('APARTADOS')")
    public ResponseEntity<ApiResponse<Apartado>> confirm(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApartadoConfirmRequest req,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.confirm(id, req, actor), "Apartado confirmado"));
    }

    /**
     * Quita una línea de un apartado {@code PENDING} (ver {@link ApartadoService#removeItem}) —
     * ej. el producto se agotó antes de que se revisara la solicitud, y el cajero prefiere
     * quitar solo esa línea en vez de cancelar todo el apartado.
     */
    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("@sectionAccess.check('APARTADOS')")
    public ResponseEntity<ApiResponse<Apartado>> removeItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.removeItem(id, itemId, actor), "Producto quitado del apartado"));
    }

    /**
     * Completa un apartado {@code ACTIVE}: el cliente recogió y pagó, se genera la venta
     * real (ver {@link ApartadoService#complete}).
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("@sectionAccess.check('APARTADOS')")
    public ResponseEntity<ApiResponse<Apartado>> complete(
            @PathVariable Long id,
            @Valid @RequestBody ApartadoCompleteRequest req,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.complete(id, req, actor), "Apartado completado"));
    }

    /**
     * Cancela un apartado {@code PENDING} o {@code ACTIVE} (ver {@link ApartadoService#cancel}).
     * El motivo ({@code reason}) es opcional — si el cliente dejó correo al solicitar el
     * apartado, se le avisa la cancelación incluyéndolo.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("@sectionAccess.check('APARTADOS')")
    public ResponseEntity<ApiResponse<Apartado>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApartadoCancelRequest req,
            @AuthenticationPrincipal User actor) {
        String reason = req != null ? req.getReason() : null;
        return ResponseEntity.ok(ApiResponse.ok(apartadoService.cancel(id, reason, actor), "Apartado cancelado"));
    }
}
