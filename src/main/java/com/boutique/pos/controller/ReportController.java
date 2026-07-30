package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // usado también por el widget del Dashboard, por eso admite ambas secciones
    @GetMapping("/sales-summary")
    @PreAuthorize("@sectionAccess.checkAny('REPORTS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> salesSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesSummary(from, to, actor), null));
    }

    @GetMapping("/top-products")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.topProducts(from, to, limit, actor), null));
    }

    @GetMapping("/sales-by-day")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesByDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesByDay(from, to, actor), null));
    }

    @GetMapping("/inventory-status")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventoryStatus(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.inventoryStatus(actor), null));
    }

    @GetMapping("/inventory-movements/{productId}")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> movements(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.movementsByProduct(productId, from, to, actor), null));
    }
}
