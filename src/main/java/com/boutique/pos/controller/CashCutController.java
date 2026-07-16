package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.CashCutRequest;
import com.boutique.pos.dto.CashCutSummary;
import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.User;
import com.boutique.pos.service.CashCutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cash-cuts")
@RequiredArgsConstructor
public class CashCutController {

    private final CashCutService cashCutService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CashCut>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.findAll(pageable).getContent(), null));
    }

    @GetMapping("/open")
    public ResponseEntity<ApiResponse<CashCut>> getOpen() {
        Optional<CashCut> open = cashCutService.findOpen();
        return ResponseEntity.ok(ApiResponse.ok(open.orElse(null), null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CashCut>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.findById(id), null));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<ApiResponse<CashCutSummary>> summary(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.summary(id), null));
    }

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<CashCut>> open(@Valid @RequestBody CashCutRequest req,
                                                      @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.open(req, actor), "Corte abierto"));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CashCut>> close(@PathVariable Long id,
                                                       @RequestBody CashCutRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(cashCutService.close(id, req, actor), "Corte cerrado"));
    }
}
