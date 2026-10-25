package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.BarcodeLabelRequest;
import com.boutique.pos.service.BarcodeLabelPdfService;
import com.boutique.pos.service.ProductBarcodeService;
import com.boutique.pos.service.StockImportService;
import com.boutique.pos.service.InventoryAuditService;
import com.boutique.pos.model.InventoryAuditAction;
import com.boutique.pos.dto.StockImportResult;
import com.boutique.pos.dto.BulkImportResult;
import com.boutique.pos.dto.InventoryAdjustRequest;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.ProductRequest;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.ProductImage;
import com.boutique.pos.model.Tienda;
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
import com.boutique.pos.util.SortParams;
import org.springframework.data.domain.Sort;
import java.util.Map;

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
    private final ProductBarcodeService productBarcodeService;
    private final BarcodeLabelPdfService barcodeLabelPdfService;
    private final StockImportService stockImportService;
    private final InventoryAuditService auditService;

    // Tope de etiquetas por PDF, para no armar un archivo gigante por error (ej. 500
    // copias de todo el catálogo).
    private static final int MAX_LABELS_PER_PDF = 5000;

    // Columnas ordenables de la tabla de Inventario (clave del frontend -> propiedad JPQL).
    private static final Map<String, String> SORT_PRODUCTS = Map.of("name", "name", "category", "category.name",
            "barcode", "barcode", "price", "price", "stock", "stock", "minStock", "minStock");
    private static final Sort DEFAULT_SORT_PRODUCTS = Sort.by("name");

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
        // Alfabético explícito: la consulta no trae ORDER BY propio (lo comparte la tabla de Inventario).
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
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
                                                                    @RequestParam(required = false) String sort,
                                                                    @RequestParam(required = false) String dir,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size,
                                                                    @AuthenticationPrincipal User actor) {
        Pageable pageable = SortParams.page(page, size, sort, dir, SORT_PRODUCTS, DEFAULT_SORT_PRODUCTS);
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
        // llaman a ProductService#create directo y pueden crearlo en 0. Un producto "por
        // pedido" tampoco exige stock — nunca se fabrica/trae hasta que alguien lo pide,
        // así que se queda en 0 para siempre (ver Product#getIsMadeToOrder).
        if (!Boolean.TRUE.equals(req.getIsMadeToOrder()) && (req.getStock() == null || req.getStock() < 1)) {
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
     * Aplica un ajuste manual de stock (entrada o salida) sobre un producto. Requiere la
     * acción "Ajustar stock" (ADJUST_STOCK) en INVENTORY — separada de EDIT, que solo
     * edita los datos del producto (ver {@link #create} para el criterio).
     *
     * @param id    identificador del producto a ajustar
     * @param req   datos del ajuste (cantidad, motivo, etc.)
     * @param actor usuario autenticado que realiza el ajuste
     */
    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'ADJUST_STOCK')")
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
        auditService.log(actor, InventoryAuditAction.EXPORT, "Descargó el inventario en Excel");
        return ResponseEntity.ok()
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=\"inventario-" + LocalDate.now() + ".xlsx\"")
                .body(inventoryCountSheetService.buildExcel(actor));
    }

    /** Igual que {@link #countSheetExcel}, en PDF listo para imprimir. */
    @GetMapping("/count-sheet.pdf")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<byte[]> countSheetPdf(@AuthenticationPrincipal User actor) {
        auditService.log(actor, InventoryAuditAction.EXPORT, "Descargó el inventario en PDF");
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"inventario-" + LocalDate.now() + ".pdf\"")
                .body(inventoryCountSheetService.buildPdf(actor));
    }

    /** Cuántos productos activos de la tienda no tienen código todavía. */
    @GetMapping("/barcodes/missing-count")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<ApiResponse<Long>> barcodesMissingCount(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(productBarcodeService.countMissing(actor)));
    }

    /**
     * Asigna un código consecutivo a todos los productos activos de la tienda que no tengan,
     * en orden alfabético (ver {@link ProductBarcodeService}). Mismo permiso que editar un
     * producto.
     */
    @PostMapping("/barcodes/generate")
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'EDIT')")
    public ResponseEntity<ApiResponse<Integer>> generateBarcodes(@AuthenticationPrincipal User actor) {
        int generated = productBarcodeService.generateMissing(actor);
        String msg = generated == 0 ? "Todos los productos ya tienen código"
                : "Se generaron " + generated + " código" + (generated == 1 ? "" : "s");
        return ResponseEntity.ok(ApiResponse.ok(generated, msg));
    }

    /**
     * Hoja tamaño carta con nombre y código de barras de todos los productos activos con
     * código, en orden alfabético — para imprimirla y escanear desde ella en caja.
     */
    @GetMapping("/barcode-sheet.pdf")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<byte[]> barcodeSheet(@AuthenticationPrincipal User actor) {
        List<Product> products = productService.findForLabels(null, actor);
        Tienda tienda = products.get(0).getTienda();
        String title = "Códigos de productos" + (tienda != null ? " — " + tienda.getName() : "") + " — " + LocalDate.now();
        auditService.log(actor, InventoryAuditAction.EXPORT, "Descargó la hoja de códigos (" + products.size() + " productos)");
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"codigos-" + LocalDate.now() + ".pdf\"")
                .body(barcodeLabelPdfService.buildSheet(products, title));
    }

    /**
     * PDF de etiquetas de código de barras (una por página, del tamaño de la etiqueta): de
     * los productos indicados o, si no se indica ninguno, de todos los que tienen código.
     */
    @PostMapping("/barcode-labels")
    @PreAuthorize("@sectionAccess.check('INVENTORY')")
    public ResponseEntity<byte[]> barcodeLabels(@Valid @RequestBody BarcodeLabelRequest req,
                                                @AuthenticationPrincipal User actor) {
        List<Product> products = productService.findForLabels(req.getProductIds(), actor);
        int copies = req.getCopies() != null ? req.getCopies() : 1;
        if ((long) products.size() * copies > MAX_LABELS_PER_PDF) {
            throw new IllegalArgumentException("Máximo " + MAX_LABELS_PER_PDF + " etiquetas por impresión; reduce las copias por producto");
        }
        if (products.size() == 1) {
            auditService.log(actor, InventoryAuditAction.LABELS_PRINTED, products.get(0), copies + " etiqueta(s)");
        } else {
            auditService.log(actor, InventoryAuditAction.LABELS_PRINTED,
                    products.size() + " productos × " + copies + " = " + (products.size() * copies) + " etiqueta(s)");
        }
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"etiquetas-" + LocalDate.now() + ".pdf\"")
                .body(barcodeLabelPdfService.build(products, copies, req.getSize()));
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

    /**
     * "Actualizar existencias": sube la hoja de inventario en Excel con la columna "Cantidad
     * real" llenada y deja el stock de cada producto en esa cantidad (ver {@link
     * StockImportService}). Mismo permiso que ajustar stock (INVENTORY:ADJUST_STOCK).
     */
    @PreAuthorize("@sectionAccess.checkAction('INVENTORY', 'ADJUST_STOCK')")
    @PostMapping(value = "/stock-import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<StockImportResult>> stockImport(@RequestParam("file") MultipartFile file,
                                                                      @AuthenticationPrincipal User actor) {
        StockImportResult result = stockImportService.importFile(file, actor);
        String message = result.getUpdated() + " producto(s) actualizado(s), " + result.getUnchanged() + " sin cambio"
                + (result.getErrorCount() > 0 ? ", " + result.getErrorCount() + " con error" : "");
        return ResponseEntity.ok(ApiResponse.ok(result, message));
    }
}
