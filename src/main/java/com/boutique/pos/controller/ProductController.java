package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.BulkImportResult;
import com.boutique.pos.dto.InventoryAdjustRequest;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.ProductRequest;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.ProductImage;
import com.boutique.pos.model.User;
import com.boutique.pos.security.SectionAccessService;
import com.boutique.pos.service.InventoryCountSheetService;
import com.boutique.pos.service.ProductBulkImportService;
import com.boutique.pos.service.ProductImageService;
import com.boutique.pos.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ProductImageService productImageService;
    private final ProductBulkImportService productBulkImportService;
    private final SectionAccessService sectionAccess;
    private final InventoryCountSheetService inventoryCountSheetService;

    // Ventana para que un rol con CREATE (sin EDIT) suba las fotos del producto que acaba de
    // crear — ver uploadImage. Holgada para varias fotos pesadas en una conexión lenta.
    private static final Duration NEW_PRODUCT_IMAGE_WINDOW = Duration.ofMinutes(10);

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
     * Búsqueda paginada de productos activos para Inventario, con filtros opcionales por
     * texto libre, categoría, stock bajo, y ahora también por historial de ventas
     * ({@code sold=NEVER_SOLD} o {@code sold=TOP_SELLERS}) — a diferencia de {@link #list}
     * (catálogo completo sin paginar) y {@link #search} (paginado pero sin filtro de
     * ventas, usado por POS/Dashboard), este endpoint pagina en el servidor y devuelve los
     * metadatos de paginación completos, para que Inventario no tenga que cargar todo el
     * catálogo de un jalón conforme crece. Accesible desde {@code INVENTORY}.
     *
     * @param q          texto de búsqueda libre (nombre, código de barras), opcional
     * @param categoryId id de categoría para filtrar, opcional
     * @param lowStock   si es {@code true}, limita el resultado a productos con stock bajo
     * @param sold       {@code "NEVER_SOLD"} o {@code "TOP_SELLERS"}, opcional
     * @param page       número de página (base 0, por defecto 0)
     * @param size       tamaño de página (por defecto 20)
     * @param actor      usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/page")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<PageResponse<Product>>> page(@RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) Long categoryId,
                                                                    @RequestParam(required = false) Boolean lowStock,
                                                                    @RequestParam(required = false) String sold,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size,
                                                                    @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.searchPage(q, categoryId, lowStock, sold, pageable, actor);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Busca stock disponible de un producto en las tiendas "hermanas" de la del actor (el
     * mismo SUPERVISOR), sin exponer nada más de ellas. Accesible para cualquier rol con la
     * sección {@code INVENTORY} habilitada (cajero, vendedor, admin...), no solo SUPER_ADMIN/
     * SUPERVISOR — ver {@link ProductService#searchSiblingStock}.
     *
     * @param q      texto de búsqueda (nombre o código de barras), obligatorio
     * @param page   número de página (base 0, por defecto 0)
     * @param size   tamaño de página (por defecto 20)
     * @param actor  usuario autenticado; determina el grupo de tiendas hermanas
     */
    @GetMapping("/sibling-stock")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<PageResponse<Product>>> siblingStock(@RequestParam String q,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size,
                                                                            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.searchSiblingStock(q, actor, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Total histórico de unidades vendidas de cada producto activo (incluye los que nunca
     * se han vendido, con 0), para alimentar los filtros "sin ventas" / "más vendidos" de
     * Inventario. Accesible desde {@code INVENTORY}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/sales-stats")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesStats(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.salesStats(actor), null));
    }

    /**
     * Piezas actualmente descontadas del stock por apartados {@code ACTIVE} de cada
     * producto activo, para la columna "Apartados" de Inventario (no incluye {@code
     * PENDING}: todavía no descuenta stock real). Accesible desde {@code INVENTORY}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/reserved-stats")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<List<Object[]>>> reservedStats(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.reservedStats(actor), null));
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
     * Crea un nuevo producto en la tienda del usuario autenticado. Requiere la acción
     * CREATE otorgada en INVENTORY (siempre disponible para ADMIN/SUPER_ADMIN/SUPERVISOR;
     * para cualquier otro rol, ej. CASHIER, debe otorgarse explícitamente desde "Roles y
     * Permisos" — ver {@code SectionAccessService#checkAction}).
     *
     * @param req   datos del producto a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'CREATE')")
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        // Solo el alta desde el formulario exige stock inicial: la carga masiva y la
        // conversión de materia prima (que crea el producto en 0 y luego le suma piezas)
        // llaman a ProductService#create directo y pueden crearlo en 0.
        if (req.getStock() == null || req.getStock() < 1) {
            throw new FieldConflictException("stock", "Captura el stock inicial (mínimo 1)");
        }
        return ResponseEntity.ok(ApiResponse.ok(productService.create(req, actor), "Producto creado"));
    }

    /**
     * Actualiza los datos de un producto existente. Requiere la acción EDIT otorgada en
     * INVENTORY (ver {@link #create} para el criterio).
     *
     * @param id    identificador del producto a actualizar
     * @param req   nuevos datos del producto
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'EDIT')")
    public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,
                                                        @Valid @RequestBody ProductRequest req,
                                                        @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, req, actor), "Producto actualizado"));
    }

    /**
     * Aplica un ajuste manual de stock (entrada o salida) sobre un producto. Cuenta como
     * una edición del producto — requiere la acción EDIT otorgada en INVENTORY (ver
     * {@link #create} para el criterio).
     *
     * @param id    identificador del producto a ajustar
     * @param req   datos del ajuste (cantidad, motivo, etc.)
     * @param actor usuario autenticado que realiza el ajuste
     */
    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'EDIT')")
    public ResponseEntity<ApiResponse<Product>> adjustStock(@PathVariable Long id,
                                                             @Valid @RequestBody InventoryAdjustRequest req,
                                                             @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productService.adjustStock(id, req, actor), "Stock ajustado"));
    }

    /**
     * Desactiva (baja lógica) un producto existente. Requiere la acción DELETE otorgada
     * en INVENTORY (ver {@link #create} para el criterio).
     *
     * @param id    identificador del producto a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'DELETE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        productService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Producto desactivado"));
    }

    // ── Fotos del producto (galería para la tienda pública de apartados) ───────────────
    // Todas validan primero que el producto sea de la tienda del actor (productService.findById(id, actor)
    // ya lanza si no) antes de tocar nada en ProductImageService.

    /** Fotos de un producto, portada primero. Accesible desde {@code INVENTORY}. */
    @GetMapping("/{id}/images")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<List<ProductImage>>> listImages(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        Product product = productService.findById(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(productImageService.list(product.getId()), null));
    }

    /**
     * Sube una foto nueva para el producto. Requiere la acción EDIT en INVENTORY (la
     * galería se administra como parte de editar el producto) — o, para quien solo tiene
     * CREATE, únicamente sobre un producto que él mismo acaba de dar de alta: al agregar un
     * producto, Inventory.jsx primero lo crea y luego sube las fotos elegidas, así que sin
     * esta excepción un rol con CREATE y sin EDIT creaba el producto pero se quedaba sin
     * fotos (403). El límite de tiempo evita que CREATE sirva para tocar la galería de
     * productos que ya existían.
     */
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'EDIT') or @sectionAccess.checkAction('INVENTORY', 'CREATE')")
    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProductImage>> uploadImage(@PathVariable Long id,
                                                                  @RequestParam("file") MultipartFile file,
                                                                  @AuthenticationPrincipal User actor) {
        Product product = productService.findById(id, actor);
        if (!sectionAccess.checkAction("INVENTORY", "EDIT") && !isFreshlyCreatedBy(product, actor)) {
            throw new AccessDeniedException("Solo puedes agregar fotos a un producto que acabas de crear");
        }
        return ResponseEntity.ok(ApiResponse.ok(productImageService.upload(product, file), "Foto agregada"));
    }

    /** {@code true} si el producto lo creó {@code actor} hace menos de {@link #NEW_PRODUCT_IMAGE_WINDOW}. */
    private boolean isFreshlyCreatedBy(Product product, User actor) {
        return product.getCreatedBy() != null && product.getCreatedBy().getId().equals(actor.getId())
                && product.getCreatedAt() != null
                && product.getCreatedAt().isAfter(LocalDateTime.now().minus(NEW_PRODUCT_IMAGE_WINDOW));
    }

    /** Marca una foto como portada del producto. Requiere la acción EDIT en INVENTORY. */
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'EDIT')")
    @PutMapping("/{id}/images/{imageId}/primary")
    public ResponseEntity<ApiResponse<Void>> setPrimaryImage(@PathVariable Long id, @PathVariable Long imageId,
                                                              @AuthenticationPrincipal User actor) {
        Product product = productService.findById(id, actor);
        productImageService.setPrimary(product, imageId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Portada actualizada"));
    }

    /** Borra una foto del producto. Requiere la acción EDIT en INVENTORY (igual que subir
     *  o marcar portada — administrar la galería es parte de editar el producto). */
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'EDIT')")
    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable Long id, @PathVariable Long imageId,
                                                          @AuthenticationPrincipal User actor) {
        Product product = productService.findById(id, actor);
        productImageService.delete(product, imageId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Foto eliminada"));
    }

    // ── Carga masiva de productos por Excel ────────────────────────────────────────────

    /**
     * Descarga la plantilla (.xlsx) de carga masiva, con las columnas esperadas y una fila
     * de ejemplo. Requiere el mismo permiso que crear un producto — es de solo lectura, sin
     * ningún riesgo, a diferencia de la carga real (ver {@link #bulkImport}).
     */
    @GetMapping("/bulk-import/template")
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'CREATE')")
    public ResponseEntity<byte[]> bulkImportTemplate() {
        byte[] xlsx = productBulkImportService.buildTemplate();
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"plantilla-carga-masiva-productos.xlsx\"")
                .body(xlsx);
    }

    /**
     * Descarga la hoja de conteo físico de inventario en Excel: nombre del producto, cantidad
     * actual en sistema y "Cantidad real" en blanco. Libre para cualquiera con acceso a
     * INVENTORY (sin acción específica): es de solo lectura, pensada para que el propio
     * cajero haga el conteo.
     */
    @GetMapping("/count-sheet.xlsx")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<byte[]> countSheetExcel(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"inventario-" + LocalDate.now() + ".xlsx\"")
                .body(inventoryCountSheetService.buildExcel(actor));
    }

    /** Igual que {@link #countSheetExcel}, en PDF listo para imprimir. */
    @GetMapping("/count-sheet.pdf")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<byte[]> countSheetPdf(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"inventario-" + LocalDate.now() + ".pdf\"")
                .body(inventoryCountSheetService.buildPdf(actor));
    }

    /**
     * Procesa un archivo de carga masiva de productos, creando uno por cada fila válida y
     * reportando el resto como errores (sin tumbar la carga completa por una fila mala).
     * Mismo permiso que crear un producto individual (INVENTORY:CREATE).
     */
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'CREATE')")
    @PostMapping(value = "/bulk-import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<BulkImportResult>> bulkImport(@RequestParam("file") MultipartFile file,
                                                                     @AuthenticationPrincipal User actor) {
        BulkImportResult result = productBulkImportService.importFile(file, actor);
        String message = result.getErrorCount() == 0
                ? result.getCreated() + " producto(s) creado(s) correctamente"
                : result.getCreated() + " producto(s) creado(s), " + result.getErrorCount() + " con error";
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }
}
