package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.User;
import com.boutique.pos.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    @PreAuthorize("@sectionAccess.check('SALES')")
    public ResponseEntity<ApiResponse<List<Sale>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(saleService.findAll(from, to, pageable).getContent(), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('SALES')")
    public ResponseEntity<ApiResponse<Sale>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.findById(id), null));
    }

    @PostMapping
    @PreAuthorize("@sectionAccess.check('POS')")
    public ResponseEntity<ApiResponse<Sale>> create(@Valid @RequestBody SaleRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.create(req, actor), "Venta registrada"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Sale>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(saleService.cancel(id), "Venta cancelada"));
    }
}
