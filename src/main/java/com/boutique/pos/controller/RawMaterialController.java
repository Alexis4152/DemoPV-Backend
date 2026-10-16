package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.InventoryAdjustRequest;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.RawMaterialRequest;
import com.boutique.pos.dto.RawMaterialTransferRequest;
import com.boutique.pos.model.RawMaterial;
import com.boutique.pos.model.RawMaterialMovement;
import com.boutique.pos.model.User;
import com.boutique.pos.security.SectionAccessService;
import com.boutique.pos.service.RawMaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Materia prima (sección {@code RAW_MATERIALS}): inventario por piezas que no está a la
 * venta. Mismos permisos finos que Inventario (crear/editar/eliminar por rol).
 */
@RestController
@RequestMapping("/api/raw-materials")
@RequiredArgsConstructor
public class RawMaterialController {

    private final RawMaterialService rawMaterialService;
    private final SectionAccessService sectionAccess;

    @GetMapping("/page")
    @PreAuthorize("@sectionAccess.check('RAW_MATERIALS')")
    public ResponseEntity<ApiResponse<PageResponse<RawMaterial>>> page(@RequestParam(required = false) String q,
                                                                        @RequestParam(required = false) Long categoryId,
                                                                        @RequestParam(required = false) Boolean lowStock,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size,
                                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                rawMaterialService.search(q, categoryId, lowStock, PageRequest.of(page, Math.min(size, 100)), actor)), null));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("@sectionAccess.check('RAW_MATERIALS')")
    public ResponseEntity<ApiResponse<List<RawMaterial>>> lowStock(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(rawMaterialService.lowStock(actor), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.check('RAW_MATERIALS')")
    public ResponseEntity<ApiResponse<RawMaterial>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(rawMaterialService.findById(id, actor), null));
    }

    @GetMapping("/{id}/movements")
    @PreAuthorize("@sectionAccess.check('RAW_MATERIALS')")
    public ResponseEntity<ApiResponse<PageResponse<RawMaterialMovement>>> movements(@PathVariable Long id,
                                                                                    @RequestParam(defaultValue = "0") int page,
                                                                                    @RequestParam(defaultValue = "10") int size,
                                                                                    @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                rawMaterialService.movements(id, PageRequest.of(page, Math.min(size, 100)), actor)), null));
    }

    @PostMapping
    @PreAuthorize("@sectionAccess.checkAction('RAW_MATERIALS', 'CREATE')")
    public ResponseEntity<ApiResponse<RawMaterial>> create(@Valid @RequestBody RawMaterialRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(rawMaterialService.create(req, actor), "Materia prima creada"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('RAW_MATERIALS', 'EDIT')")
    public ResponseEntity<ApiResponse<RawMaterial>> update(@PathVariable Long id, @Valid @RequestBody RawMaterialRequest req,
                                                           @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(rawMaterialService.update(id, req, actor), "Materia prima actualizada"));
    }

    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("@sectionAccess.checkAction('RAW_MATERIALS', 'EDIT')")
    public ResponseEntity<ApiResponse<RawMaterial>> adjustStock(@PathVariable Long id, @Valid @RequestBody InventoryAdjustRequest req,
                                                                @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(rawMaterialService.adjustStock(id, req, actor), "Stock ajustado"));
    }

    /**
     * Pasa piezas al inventario vendible. Además de editar materia prima, pide permiso en
     * Inventario: crear (si el producto es nuevo) o editar (si ya existe).
     */
    @PostMapping("/{id}/to-inventory")
    @PreAuthorize("@sectionAccess.checkAction('RAW_MATERIALS', 'EDIT')")
    public ResponseEntity<ApiResponse<RawMaterial>> toInventory(@PathVariable Long id, @Valid @RequestBody RawMaterialTransferRequest req,
                                                                @AuthenticationPrincipal User actor) {
        boolean allowed = req.getProductId() == null
                ? sectionAccess.checkAction("INVENTORY", "CREATE")
                : sectionAccess.checkAction("INVENTORY", "EDIT");
        if (!allowed) {
            throw new AccessDeniedException(req.getProductId() == null
                    ? "No tienes permiso para crear productos en Inventario"
                    : "No tienes permiso para editar productos en Inventario");
        }
        return ResponseEntity.ok(ApiResponse.ok(rawMaterialService.transferToInventory(id, req, actor), "Piezas pasadas a inventario"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('RAW_MATERIALS', 'DELETE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        rawMaterialService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Materia prima desactivada"));
    }
}
