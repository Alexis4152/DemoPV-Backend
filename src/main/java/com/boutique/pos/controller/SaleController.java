package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.SaleService;
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

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    @PreAuthorize("@sectionAccess.check('SALES')")
    public ResponseEntity<ApiResponse<PageResponse<Sale>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Sale> result = saleService.findAll(from, to, customerName, paymentMethod, status, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('SALES')")
    public ResponseEntity<ApiResponse<Sale>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.findById(id, actor), null));
    }

    @PostMapping
    @PreAuthorize("@sectionAccess.check('POS')")
    public ResponseEntity<ApiResponse<Sale>> create(@Valid @RequestBody SaleRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.create(req, actor), "Venta registrada"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Sale>> cancel(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.cancel(id, actor), "Venta cancelada"));
    }
}
