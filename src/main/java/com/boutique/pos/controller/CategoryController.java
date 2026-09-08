package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.CategoryRequest;
import com.boutique.pos.model.Category;
import com.boutique.pos.model.User;
import com.boutique.pos.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador de categorías de producto, expuesto bajo {@code /api/categories}.
 * <p>
 * La consulta está disponible tanto desde Inventario como desde el Punto de
 * Venta (para poder filtrar productos por categoría al vender); la creación,
 * edición y eliminación quedan reservadas al rol ADMIN. Todas las operaciones
 * están acotadas a la tienda del usuario autenticado.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Lista todas las categorías de la tienda del usuario autenticado.
     * Accesible desde las secciones {@code INVENTORY} o {@code POS}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<List<Category>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findAll(actor), null));
    }

    /**
     * Obtiene el detalle de una categoría por su id, dentro de la tienda del
     * usuario autenticado.
     *
     * @param id    identificador de la categoría
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<Category>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.findById(id, actor), null));
    }

    /**
     * Crea una nueva categoría en la tienda del usuario autenticado. Solo
     * disponible para el rol ADMIN.
     *
     * @param req   datos de la categoría a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Category>> create(@Valid @RequestBody CategoryRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.create(req, actor), "Categoría creada"));
    }

    /**
     * Actualiza los datos de una categoría existente. Solo disponible para el
     * rol ADMIN.
     *
     * @param id    identificador de la categoría a actualizar
     * @param req   nuevos datos de la categoría
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Category>> update(@PathVariable Long id,
                                                         @Valid @RequestBody CategoryRequest req,
                                                         @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(id, req, actor), "Categoría actualizada"));
    }

    /**
     * Elimina una categoría existente. Solo disponible para el rol ADMIN.
     *
     * @param id    identificador de la categoría a eliminar
     * @param actor usuario autenticado que realiza la eliminación
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        categoryService.delete(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Categoría eliminada"));
    }
}
