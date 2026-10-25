package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.ReimbursementPayRequest;
import com.boutique.pos.model.Reimbursement;
import com.boutique.pos.model.ReimbursementStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ReimbursementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import com.boutique.pos.util.SortParams;
import org.springframework.data.domain.Sort;
import java.util.Map;

/**
 * Reembolsos ({@code /api/reimbursements}): dinero que la tienda debe a quien puso efectivo
 * de su bolsa. Consultarlos lo necesita cualquiera con {@code CASH_CUTS} (el cajero los ve
 * en la salida "Reembolso" de su corte); pagarlos desde aquí, solo administradores.
 */
@RestController
@RequestMapping("/api/reimbursements")
@RequiredArgsConstructor
public class ReimbursementController {

    // Columnas ordenables de la tabla (clave que manda el frontend -> propiedad JPQL);
    // cualquier otra clave usa el orden por defecto. Ver SortParams.
    private static final Map<String, String> SORT_REIMBURSEMENTS = Map.of("person", "rp.name", "date", "createdAt", "amount", "amount");
    private static final Sort DEFAULT_SORT_REIMBURSEMENTS = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ReimbursementService reimbursementService;

    @GetMapping
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<PageResponse<Reimbursement>>> list(@RequestParam(required = false) ReimbursementStatus status,
                                                                         @RequestParam(required = false) String sort,
                                                                         @RequestParam(required = false) String dir,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size,
                                                                         @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(reimbursementService.search(status, SortParams.page(page, size, sort, dir, SORT_REIMBURSEMENTS, DEFAULT_SORT_REIMBURSEMENTS), actor)), null));
    }

    @GetMapping("/pending-total")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<BigDecimal>> pendingTotal(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reimbursementService.pendingTotal(actor), null));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Reimbursement>> pay(@PathVariable Long id,
                                                          @Valid @RequestBody ReimbursementPayRequest req,
                                                          @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reimbursementService.pay(id, req, actor), "Reembolso pagado"));
    }
}
