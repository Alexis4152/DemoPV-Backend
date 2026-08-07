package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.CashCutRequest;
import com.boutique.pos.dto.CashCutSummary;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.CashCutService;
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

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/cash-cuts")
@RequiredArgsConstructor
public class CashCutController {

    private final CashCutService cashCutService;

    // tabla/historial de cortes: solo ADMIN (de su propia tienda) o SUPER_ADMIN (todas)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<CashCut>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) CashCutStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CashCut> result = cashCutService.findAll(from, to, status, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    // usado también por el widget del Dashboard, por eso admite ambas secciones
    @GetMapping("/open")
    @PreAuthorize("@sectionAccess.checkAny('CASH_CUTS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<CashCut>> getOpen(@AuthenticationPrincipal User actor) {
        Optional<CashCut> open = cashCutService.findOpen(actor);
        return ResponseEntity.ok(ApiResponse.ok(open.orElse(null), null));
    }

    // corte propio del día de hoy (abierto o ya cerrado) — para que quien lo cerró lo pueda seguir viendo
    @GetMapping("/mine/today")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> getMineToday(@AuthenticationPrincipal User actor) {
        Optional<CashCut> mine = cashCutService.findMineToday(actor);
        return ResponseEntity.ok(ApiResponse.ok(mine.orElse(null), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.findById(id, actor), null));
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCutSummary>> summary(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.summary(id, actor), null));
    }

    @PostMapping("/open")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> open(@Valid @RequestBody CashCutRequest req,
                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.open(req, actor), "Corte abierto"));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@sectionAccess.check('CASH_CUTS')")
    public ResponseEntity<ApiResponse<CashCut>> close(@PathVariable Long id,
                                                       @RequestBody CashCutRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.close(id, req, actor), "Corte cerrado"));
    }
}
