package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.CashMovementRequest;
import com.boutique.pos.dto.CashRegisterRequest;
import com.boutique.pos.dto.CashRegisterStatusDto;
import com.boutique.pos.dto.HandoverDto;
import com.boutique.pos.dto.HandoverResolveRequest;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.model.CashMovement;
import com.boutique.pos.model.CashRegister;
import com.boutique.pos.model.User;
import com.boutique.pos.service.CashMovementService;
import com.boutique.pos.service.CashRegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cajas de la tienda ({@code /api/cash-registers}): su estado, alta/edición, movimientos
 * con la caja cerrada y las entregas entre turnos. Ver el estado de las cajas y la lista
 * de personal lo necesita cualquiera con {@code CASH_CUTS} (para elegir caja al abrir y
 * para los selects de aportes); el resto es solo de administradores/supervisores.
 */
@RestController
@RequestMapping("/api/cash-registers")
@RequiredArgsConstructor
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;
    private final CashMovementService cashMovementService;

    @GetMapping
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<List<CashRegisterStatusDto>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashRegisterService.listStatus(actor), null));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<CashRegister>> create(@Valid @RequestBody CashRegisterRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashRegisterService.create(req, actor), "Caja creada"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<CashRegister>> update(@PathVariable Long id,
                                                             @Valid @RequestBody CashRegisterRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashRegisterService.update(id, req, actor), "Caja actualizada"));
    }

    /** Retiro o entrada de efectivo con la caja cerrada (entre turnos). */
    @PostMapping("/{id}/movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<CashMovement>> closedMovement(@PathVariable Long id,
                                                                     @Valid @RequestBody CashMovementRequest req,
                                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashMovementService.registerOnClosedRegister(id, req, actor), "Movimiento registrado"));
    }

    /** Personal de la tienda para los selects de quién puso/retiró/pagó dinero. */
    @GetMapping("/people")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<List<CashRegisterService.PersonDto>>> people(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashRegisterService.people(actor), null));
    }

    @GetMapping("/handovers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<PageResponse<HandoverDto>>> handovers(@RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "10") int size,
                                                                            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(cashRegisterService.handovers(PageRequest.of(page, size), actor)), null));
    }

    @GetMapping("/handovers/unresolved-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Long>> unresolvedCount(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashRegisterService.unresolvedCount(actor), null));
    }

    @PostMapping("/handovers/{cutId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<HandoverDto>> resolve(@PathVariable Long cutId,
                                                            @Valid @RequestBody HandoverResolveRequest req,
                                                            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashRegisterService.resolveHandover(cutId, req, actor), "Diferencia resuelta"));
    }
}
