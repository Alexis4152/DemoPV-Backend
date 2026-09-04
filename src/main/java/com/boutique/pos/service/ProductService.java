package com.boutique.pos.service;

import com.boutique.pos.dto.InventoryAdjustRequest;
import com.boutique.pos.dto.ProductRequest;
import com.boutique.pos.model.MovementType;
import com.boutique.pos.model.Category;
import com.boutique.pos.model.InventoryMovement;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de productos y control de inventario, con aislamiento por tienda (multi-tenancy).
 *
 * <p>Cada producto pertenece a una sola tienda y a una {@link Category} de esa misma
 * tienda. Todo cambio de stock (alta con stock inicial, ajuste manual, o los que
 * disparan {@link SaleService}/cancelaciones) queda respaldado por un {@link
 * InventoryMovement} que conserva el stock anterior y el nuevo, para tener trazabilidad
 * completa de por qué cambió el inventario. La eliminación de productos es siempre
 * borrado suave.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final InventoryMovementRepository movementRepository;
    private final TenantScope tenantScope;

    /**
     * Búsqueda paginada de productos activos, con filtros combinables.
     *
     * @param q texto de búsqueda (nombre/código de barras, según implemente el repositorio), o null
     * @param categoryId filtro por categoría, o null para no filtrar
     * @param lowStock si es true, limita a productos con stock por debajo de su mínimo; null/false no filtra
     * @param pageable paginación y orden solicitados
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return página de productos activos que cumplen los filtros
     */
    public Page<Product> search(String q, Long categoryId, Boolean lowStock, Pageable pageable, User actor) {
        return productRepository.searchActive(q, categoryId, lowStock, tenantScope.scopeId(actor), pageable);
    }

    /**
     * Lista todos los productos activos visibles para el actor.
     *
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return productos activos dentro del alcance del actor
     */
    public List<Product> findAll(User actor) {
        return productRepository.findAllActive(tenantScope.scopeId(actor));
    }

    /**
     * Busca un producto activo por su código de barras exacto, dentro de la tienda del
     * actor — pensado para los flujos de lector de código de barras (venta e inventario).
     *
     * <p>A diferencia de {@link #findById}, NO lanza excepción si no existe: un código
     * desconocido es un resultado normal al escanear (no un error), y el llamador decide
     * qué hacer (ej. ofrecer dar de alta un producto nuevo con ese código ya precargado).</p>
     *
     * @param barcode código de barras exacto a buscar
     * @param actor usuario que consulta; acota la búsqueda a su tienda
     * @return el producto encontrado, o {@code null} si ningún producto activo de su
     *         tienda tiene ese código
     */
    public Product findByBarcode(String barcode, User actor) {
        return productRepository.findByBarcodeExact(barcode, tenantScope.scopeId(actor)).orElse(null);
    }

    /**
     * Busca un producto por id sin validar a qué tienda pertenece. Uso interno; para
     * flujos con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id del producto
     * @return el producto encontrado
     * @throws IllegalArgumentException si no existe
     */
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + id));
    }

    /**
     * Busca un producto por id validando que pertenezca a la tienda del actor.
     *
     * @param id id del producto
     * @param actor usuario que realiza la consulta
     * @return el producto encontrado, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public Product findById(Long id, User actor) {
        Product p = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (p.getTienda() == null || !scope.equals(p.getTienda().getId()))) {
            throw new IllegalArgumentException("Producto no encontrado: " + id);
        }
        return p;
    }

    /**
     * Da de alta un producto nuevo en la tienda del actor.
     *
     * <p>Aplica valores por defecto cuando no vienen en el request: stock 0, stock
     * mínimo 5, unidad "pieza". Si el producto se crea con stock inicial mayor a cero,
     * se registra automáticamente un {@link InventoryMovement} de tipo {@code IN} con
     * motivo "Stock inicial", para que el historial de movimientos explique de dónde
     * salió ese stock.</p>
     *
     * @param req datos del producto nuevo, incluyendo el id de su categoría
     * @param actor usuario que lo crea; determina la tienda del producto; queda
     *              registrado como {@code createdBy}
     * @return el producto creado
     * @throws IllegalArgumentException si la categoría no existe o no pertenece a la
     *         tienda del actor
     */
    public Product create(ProductRequest req, User actor) {
        Category cat = categoryService.findById(req.getCategoryId(), actor);
        Long tiendaId = actor.getTienda() != null ? actor.getTienda().getId() : null;
        validateBarcodeUnique(req.getBarcode(), tiendaId, null);

        Product p = new Product();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setBarcode(req.getBarcode());
        p.setPrice(req.getPrice());
        p.setCost(req.getCost() != null ? req.getCost() : java.math.BigDecimal.ZERO);
        p.setStock(req.getStock() != null ? req.getStock() : 0);
        p.setMinStock(req.getMinStock() != null ? req.getMinStock() : 5);
        p.setUnit(req.getUnit() != null ? req.getUnit() : "pieza");
        p.setCategory(cat);
        p.setTienda(actor.getTienda());
        p.setIsActive(true);
        p.setCreatedBy(actor);
        Product saved = productRepository.save(p);

        if (saved.getStock() > 0) {
            recordMovement(saved, actor, MovementType.IN, saved.getStock(), 0, "Stock inicial");
        }
        return saved;
    }

    /**
     * Actualiza los datos de un producto existente. No modifica el stock: los cambios de
     * stock siempre pasan por {@link #adjustStock(Long, InventoryAdjustRequest, User)}
     * (o por una venta/cancelación) para quedar respaldados con su movimiento de inventario.
     *
     * @param id id del producto a actualizar
     * @param req nuevos valores, incluyendo el id de su categoría (puede cambiar de categoría)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del
     *              producto; queda registrado como {@code updatedBy}
     * @return el producto actualizado
     * @throws IllegalArgumentException si el producto o la nueva categoría no existen o
     *         no pertenecen a la tienda del actor
     */
    public Product update(Long id, ProductRequest req, User actor) {
        Product p = findById(id, actor);
        Category cat = categoryService.findById(req.getCategoryId(), actor);
        Long tiendaId = p.getTienda() != null ? p.getTienda().getId() : null;
        validateBarcodeUnique(req.getBarcode(), tiendaId, p.getId());

        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setBarcode(req.getBarcode());
        p.setPrice(req.getPrice());
        p.setCost(req.getCost() != null ? req.getCost() : java.math.BigDecimal.ZERO);
        p.setMinStock(req.getMinStock() != null ? req.getMinStock() : 5);
        p.setUnit(req.getUnit() != null ? req.getUnit() : "pieza");
        p.setCategory(cat);
        p.setUpdatedBy(actor);
        return productRepository.save(p);
    }

    /**
     * Ajusta manualmente el stock de un producto (entrada o salida) y registra el
     * movimiento de inventario correspondiente.
     *
     * <p>Una cantidad positiva es una entrada ({@code IN}); una cantidad negativa es una
     * salida ({@code OUT}). Solo un usuario con rol ADMIN puede registrar salidas
     * (quitar piezas) — cualquier otro rol solo puede sumar stock. El resultado nunca
     * puede dejar el stock en negativo.</p>
     *
     * @param id id del producto a ajustar
     * @param req cantidad a ajustar (positiva=entrada, negativa=salida) y motivo del ajuste
     * @param actor usuario que hace el ajuste; debe tener acceso a la tienda del
     *              producto; queda registrado en el movimiento de inventario
     * @return el producto con el stock ya actualizado
     * @throws IllegalStateException si un no-ADMIN intenta quitar piezas, o si el
     *         ajuste dejaría el stock en negativo
     */
    @Transactional
    public Product adjustStock(Long id, InventoryAdjustRequest req, User actor) {
        if (req.getQuantity() < 0 && !"ADMIN".equals(actor.getRole().getName())) {
            throw new IllegalStateException("Solo un administrador puede quitar piezas del inventario");
        }
        Product p = findById(id, actor);
        int previous = p.getStock();
        int newStock = previous + req.getQuantity();
        if (newStock < 0) throw new IllegalStateException("Stock insuficiente");
        p.setStock(newStock);
        productRepository.save(p);

        MovementType type = req.getQuantity() >= 0 ? MovementType.IN : MovementType.OUT;
        recordMovement(p, actor, type, Math.abs(req.getQuantity()), previous, req.getReason());
        return p;
    }

    /**
     * Desactiva (borrado suave) un producto: marca {@code isActive=false} y registra
     * quién y cuándo lo eliminó, sin borrar la fila ni su historial de movimientos.
     *
     * @param id id del producto a desactivar
     * @param actor usuario que lo desactiva; debe tener acceso a la tienda del producto
     */
    public void deactivate(Long id, User actor) {
        Product p = findById(id, actor);
        p.setIsActive(false);
        p.setDeletedBy(actor);
        p.setDeletedAt(java.time.LocalDateTime.now());
        productRepository.save(p);
    }

    /**
     * Valida que un código de barras (si viene) no esté ya en uso por otro producto
     * activo de la misma tienda — la unicidad es por tienda, no global, ya que dos
     * tiendas distintas pueden vender legítimamente el mismo producto de fábrica con el
     * mismo código real (ver también la restricción {@code UNIQUE(tienda_id, barcode)}
     * en {@link Product}, que actúa como segunda barrera a nivel de base de datos).
     *
     * @param barcode código a validar; si es {@code null} o está en blanco, no valida nada
     * @param tiendaId tienda contra la que se valida
     * @param excludeProductId id del propio producto a excluir de la validación (al
     *                         editar, para no chocar contra sí mismo); {@code null} al crear
     * @throws IllegalStateException si otro producto activo de la tienda ya usa ese código
     */
    private void validateBarcodeUnique(String barcode, Long tiendaId, Long excludeProductId) {
        if (barcode == null || barcode.isBlank()) return;
        Product existing = productRepository.findByBarcodeExact(barcode, tiendaId).orElse(null);
        if (existing != null && !existing.getId().equals(excludeProductId)) {
            throw new IllegalStateException("Ya existe un producto con ese código de barras: " + existing.getName());
        }
    }

    /**
     * Registra un movimiento de inventario asociado a un cambio de stock de un producto,
     * conservando el stock previo y el nuevo para trazabilidad.
     *
     * @param product producto afectado
     * @param actor usuario responsable del movimiento
     * @param type tipo de movimiento ({@code IN} o {@code OUT})
     * @param quantity cantidad movida (siempre positiva; el signo lo da {@code type})
     * @param previous stock antes del movimiento
     * @param reason motivo del movimiento, para mostrar en el historial
     */
    private void recordMovement(Product product, User actor, MovementType type,
                                 int quantity, int previous, String reason) {
        InventoryMovement mv = new InventoryMovement();
        mv.setProduct(product);
        mv.setUser(actor);
        mv.setType(type);
        mv.setQuantity(quantity);
        mv.setPreviousStock(previous);
        mv.setNewStock(previous + (type == MovementType.IN ? quantity : -quantity));
        mv.setReason(reason);
        movementRepository.save(mv);
    }
}
