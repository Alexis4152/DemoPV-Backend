package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.TiendaRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.service.TiendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tiendas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TiendaController {

    private final TiendaService tiendaService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Tienda>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findAll(), null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findById(id), null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Tienda>> create(@Valid @RequestBody TiendaRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.create(req), "Tienda creada"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> update(@PathVariable Long id, @Valid @RequestBody TiendaRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.update(id, req), "Tienda actualizada"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        tiendaService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tienda desactivada"));
    }
}
