package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.OfferRequest;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.model.Offer;
import com.boutique.pos.model.User;
import com.boutique.pos.service.OfferService;
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
import java.util.List;

/**
 * Controlador de ofertas ("compra X paga Y"), expuesto bajo {@code /api/offers}.
 * <p>
 * La consulta ({@link #list}) está disponible desde POS (para que el cajero vea qué
 * promociones están vigentes) además de desde la propia pantalla de administración; crear,
 * editar y eliminar quedan reservados a ADMIN/SUPERVISOR/SUPER_ADMIN (los únicos roles "de
 * gestión" — ver {@code SectionAccessService#checkAction}). Todas las operaciones están
 * acotadas a la tienda del usuario autenticado.
 */
@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('OFFERS', 'POS')")
    public ResponseEntity<ApiResponse<List<Offer>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.findAll(actor), null));
    }

    @GetMapping("/page")
    @PreAuthorize("@sectionAccess.check('OFFERS')")
    public ResponseEntity<ApiResponse<PageResponse<Offer>>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Offer> result = offerService.search(from, to, name, isActive, actor, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('OFFERS')")
    public ResponseEntity<ApiResponse<Offer>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.findById(id, actor), null));
    }

    @PostMapping
    @PreAuthorize("@sectionAccess.checkAction('OFFERS', 'CREATE')")
    public ResponseEntity<ApiResponse<Offer>> create(@Valid @RequestBody OfferRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.create(req, actor), "Oferta creada"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('OFFERS', 'EDIT')")
    public ResponseEntity<ApiResponse<Offer>> update(@PathVariable Long id,
                                                       @Valid @RequestBody OfferRequest req,
                                                       @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.update(id, req, actor), "Oferta actualizada"));
    }

    // Reactivar una oferta desactivada — cuenta como editarla.
    @PostMapping("/{id}/activate")
    @PreAuthorize("@sectionAccess.checkAction('OFFERS', 'EDIT')")
    public ResponseEntity<ApiResponse<Offer>> activate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.activate(id, actor), "Oferta reactivada"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('OFFERS', 'DELETE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        offerService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Oferta eliminada"));
    }
}
