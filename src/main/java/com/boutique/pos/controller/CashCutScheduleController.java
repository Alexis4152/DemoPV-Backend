package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.CashCutScheduleRequest;
import com.boutique.pos.model.CashCutSchedule;
import com.boutique.pos.service.CashCutScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Horario global (una sola fila) del cierre automático de cortes de caja — lo administra
// solo el SUPER_ADMIN, ya que aplica a todas las tiendas por igual.
@RestController
@RequestMapping("/api/cash-cuts/schedule")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CashCutScheduleController {

    private final CashCutScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<ApiResponse<CashCutSchedule>> get() {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.get(), null));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CashCutSchedule>> update(@Valid @RequestBody CashCutScheduleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.update(req), "Horario actualizado"));
    }
}
