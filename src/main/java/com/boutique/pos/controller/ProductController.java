package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.InventoryAdjustRequest;
import com.boutique.pos.dto.ProductRequest;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<List<Product>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findAll(actor), null));
    }

    // usado también por el widget de stock bajo del Dashboard
    @GetMapping("/search")
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS', 'DASHBOARD')")
    public ResponseEntity<?> search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) Boolean lowStock,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.search(q, categoryId, lowStock, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(result.getContent(), null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<Product>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findById(id, actor), null));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.create(req, actor), "Producto creado"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,
                                                        @Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, req, actor), "Producto actualizado"));
    }

    // cualquier rol con acceso a la sección de Inventario puede ajustar stock (no solo ADMIN)
    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<Product>> adjustStock(@PathVariable Long id,
                                                             @Valid @RequestBody InventoryAdjustRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.adjustStock(id, req, actor), "Stock ajustado"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        productService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Producto desactivado"));
    }
}
