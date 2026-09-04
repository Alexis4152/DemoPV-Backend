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

/**
 * Controlador de productos, expuesto bajo {@code /api/products}.
 * <p>
 * Reúne el catálogo de inventario de la tienda del usuario autenticado. La
 * consulta y búsqueda están disponibles desde Inventario, el Punto de Venta y,
 * en el caso de la búsqueda, también desde el widget de stock bajo del
 * Dashboard. La creación, edición, ajuste de stock y baja de productos están
 * restringidas según el rol o la sección de Inventario, como se indica en cada
 * endpoint.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Lista todos los productos de la tienda del usuario autenticado.
     * Accesible desde las secciones {@code INVENTORY} o {@code POS}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<List<Product>>> list(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findAll(actor), null));
    }

    // usado también por el widget de stock bajo del Dashboard
    /**
     * Búsqueda paginada de productos con filtros opcionales por texto libre,
     * categoría y stock bajo. Accesible desde {@code INVENTORY}, {@code POS} o
     * {@code DASHBOARD} (este último usado por el widget de stock bajo).
     *
     * @param q          texto de búsqueda libre (nombre, SKU, etc.), opcional
     * @param categoryId id de categoría para filtrar, opcional
     * @param lowStock   si es {@code true}, limita el resultado a productos con stock bajo
     * @param page       número de página (base 0, por defecto 0)
     * @param size       tamaño de página (por defecto 20)
     * @param actor      usuario autenticado; determina el filtro por tienda
     */
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

    /**
     * Busca un producto activo por su código de barras exacto, dentro de la tienda del
     * usuario autenticado. Pensado para los flujos de lector de código de barras (venta e
     * inventario): a diferencia de {@link #get}, no falla si no existe — regresa
     * {@code data: null}, ya que escanear un código desconocido es un resultado normal
     * (el frontend puede, por ejemplo, ofrecer dar de alta un producto nuevo con ese
     * código ya precargado). Accesible desde {@code INVENTORY} o {@code POS}.
     *
     * @param barcode código de barras exacto a buscar
     * @param actor   usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/by-barcode/{barcode}")
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<Product>> getByBarcode(@PathVariable String barcode,
                                                              @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findByBarcode(barcode, actor), null));
    }

    /**
     * Obtiene el detalle de un producto por su id, dentro de la tienda del
     * usuario autenticado.
     *
     * @param id    identificador del producto
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAny('INVENTORY', 'POS')")
    public ResponseEntity<ApiResponse<Product>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.findById(id, actor), null));
    }

    /**
     * Crea un nuevo producto en la tienda del usuario autenticado. Solo
     * disponible para el rol ADMIN.
     *
     * @param req   datos del producto a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.create(req, actor), "Producto creado"));
    }

    /**
     * Actualiza los datos de un producto existente. Solo disponible para el rol
     * ADMIN.
     *
     * @param id    identificador del producto a actualizar
     * @param req   nuevos datos del producto
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,
                                                        @Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, req, actor), "Producto actualizado"));
    }

    // cualquier rol con acceso a la sección de Inventario puede ajustar stock (no solo ADMIN)
    /**
     * Aplica un ajuste manual de stock (entrada o salida) sobre un producto.
     * A diferencia de crear/editar/eliminar, no se restringe al rol ADMIN: basta
     * con tener acceso a la sección {@code INVENTORY}.
     *
     * @param id    identificador del producto a ajustar
     * @param req   datos del ajuste (cantidad, motivo, etc.)
     * @param actor usuario autenticado que realiza el ajuste
     */
    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<Product>> adjustStock(@PathVariable Long id,
                                                             @Valid @RequestBody InventoryAdjustRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.adjustStock(id, req, actor), "Stock ajustado"));
    }

    /**
     * Desactiva (baja lógica) un producto existente. Solo disponible para el
     * rol ADMIN.
     *
     * @param id    identificador del producto a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        productService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Producto desactivado"));
    }
}
