package com.boutique.pos.service;

import com.boutique.pos.dto.InventoryAdjustRequest;
import com.boutique.pos.dto.ProductRequest;
import com.boutique.pos.dto.RawMaterialRequest;
import com.boutique.pos.dto.RawMaterialTransferRequest;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.RawMaterial;
import com.boutique.pos.model.RawMaterialMovement;
import com.boutique.pos.model.RawMaterialMovementType;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.RawMaterialMovementRepository;
import com.boutique.pos.repository.RawMaterialRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Materia prima: inventario por piezas que no está a la venta, aislado por tienda. Funciona
 * como el inventario (alta, edición, entradas/salidas, stock bajo, historial) sin precio de
 * venta ni apartados. Una parte se puede pasar al inventario vendible con {@link
 * #transferToInventory}: sale de la materia prima y entra al producto elegido, con un
 * movimiento en cada lado.
 */
@Service
@RequiredArgsConstructor
public class RawMaterialService {

    private final RawMaterialRepository rawMaterialRepository;
    private final RawMaterialMovementRepository movementRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final TenantScope tenantScope;

    public Page<RawMaterial> search(String q, Long categoryId, Boolean lowStock, Pageable pageable, User actor) {
        return rawMaterialRepository.search(blankToNull(q), categoryId, lowStock, tenantScope.scopeId(actor), pageable);
    }

    public List<RawMaterial> lowStock(User actor) {
        return rawMaterialRepository.findLowStock(tenantScope.scopeId(actor));
    }

    public RawMaterial findById(Long id, User actor) {
        RawMaterial m = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Materia prima no encontrada: " + id));
        assertInScope(m, actor);
        return m;
    }

    public Page<RawMaterialMovement> movements(Long id, Pageable pageable, User actor) {
        findById(id, actor);
        return movementRepository.findByRawMaterial_IdOrderByCreatedAtDescIdDesc(id, pageable);
    }

    @Transactional
    public RawMaterial create(RawMaterialRequest req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null) throw new IllegalStateException("Elige una tienda para poder crear materia prima");
        String barcode = blankToNull(req.getBarcode());
        validateBarcodeUnique(barcode, tienda.getId(), null);

        RawMaterial m = new RawMaterial();
        m.setTienda(tienda);
        m.setName(req.getName().trim());
        m.setDescription(blankToNull(req.getDescription()));
        m.setBarcode(barcode);
        m.setCost(req.getCost() != null ? req.getCost() : BigDecimal.ZERO);
        m.setStock(req.getStock() != null ? req.getStock() : 0);
        m.setMinStock(req.getMinStock() != null ? req.getMinStock() : 0);
        m.setCategory(req.getCategoryId() != null ? categoryService.findById(req.getCategoryId(), actor) : null);
        m.setIsActive(true);
        m.setCreatedBy(actor);
        RawMaterial saved = rawMaterialRepository.save(m);
        if (saved.getStock() > 0) {
            record(saved, actor, RawMaterialMovementType.IN, saved.getStock(), 0, "Stock inicial", null);
        }
        return saved;
    }

    /** Edita los datos; el stock solo cambia con ajustes o al pasar a inventario. */
    @Transactional
    public RawMaterial update(Long id, RawMaterialRequest req, User actor) {
        RawMaterial m = findById(id, actor);
        String barcode = blankToNull(req.getBarcode());
        validateBarcodeUnique(barcode, m.getTienda().getId(), m.getId());
        m.setName(req.getName().trim());
        m.setDescription(blankToNull(req.getDescription()));
        m.setBarcode(barcode);
        m.setCost(req.getCost() != null ? req.getCost() : BigDecimal.ZERO);
        m.setMinStock(req.getMinStock() != null ? req.getMinStock() : 0);
        m.setCategory(req.getCategoryId() != null ? categoryService.findById(req.getCategoryId(), actor) : null);
        m.setUpdatedBy(actor);
        return rawMaterialRepository.save(m);
    }

    /**
     * Entrada (cantidad positiva) o salida (negativa) manual de piezas. Igual que en el
     * inventario, solo un administrador o supervisor puede quitar piezas.
     */
    @Transactional
    public RawMaterial adjustStock(Long id, InventoryAdjustRequest req, User actor) {
        if (req.getQuantity() == 0) throw new FieldConflictException("quantity", "La cantidad debe ser distinta de cero");
        if (req.getQuantity() < 0 && !tenantScope.isAdminLevel(actor)) {
            throw new IllegalStateException("Solo un administrador puede quitar piezas de la materia prima");
        }
        findById(id, actor);
        RawMaterial m = rawMaterialRepository.findByIdForUpdate(id).orElseThrow();
        int previous = m.getStock();
        long next = (long) previous + req.getQuantity();
        if (next < 0) throw new FieldConflictException("quantity", "Stock insuficiente");
        if (next > Integer.MAX_VALUE) {
            throw new FieldConflictException("quantity", "El número es excesivamente grande — el máximo permitido es 2,147,483,647");
        }
        m.setStock((int) next);
        rawMaterialRepository.save(m);
        RawMaterialMovementType type = req.getQuantity() > 0 ? RawMaterialMovementType.IN : RawMaterialMovementType.OUT;
        record(m, actor, type, Math.abs(req.getQuantity()), previous, blankToNull(req.getReason()), null);
        return m;
    }

    /**
     * Pasa piezas de materia prima al inventario vendible: a un producto existente o a uno
     * nuevo que se crea con el costo de la materia prima. Resta de la materia prima
     * ({@code TO_INVENTORY}) y suma al producto (entrada de inventario con referencia
     * {@code MP-<movimiento>}), todo en la misma transacción. El costo de un producto que
     * ya existe no se toca.
     *
     * @throws FieldConflictException si se piden más piezas de las que hay
     * @throws IllegalStateException si no se eligió producto ni se capturaron los datos del nuevo
     */
    @Transactional
    public RawMaterial transferToInventory(Long id, RawMaterialTransferRequest req, User actor) {
        findById(id, actor);
        RawMaterial m = rawMaterialRepository.findByIdForUpdate(id).orElseThrow();
        int qty = req.getQuantity();
        if (qty > m.getStock()) {
            throw new FieldConflictException("quantity", "Solo hay " + m.getStock() + " pieza" + (m.getStock() == 1 ? "" : "s") + " de materia prima");
        }

        Product product;
        if (req.getProductId() != null) {
            product = productService.findByIdForUpdate(req.getProductId(), actor);
            if (!Boolean.TRUE.equals(product.getIsActive())) throw new IllegalStateException("Ese producto está desactivado");
        } else {
            if (blankToNull(req.getNewProductName()) == null) throw new IllegalStateException("Elige un producto o captura los datos del producto nuevo");
            if (req.getNewProductPrice() == null) throw new FieldConflictException("newProductPrice", "Captura el precio de venta");
            if (req.getNewProductCategoryId() == null) throw new FieldConflictException("newProductCategoryId", "Elige la categoría");
            ProductRequest pr = new ProductRequest();
            pr.setName(req.getNewProductName().trim());
            pr.setBarcode(req.getNewProductBarcode());
            pr.setPrice(req.getNewProductPrice());
            pr.setCost(m.getCost());
            pr.setStock(0);
            pr.setMinStock(0);
            pr.setCategoryId(req.getNewProductCategoryId());
            pr.setIsReservable(false);
            Product created = productService.create(pr, actor);
            product = productService.findByIdForUpdate(created.getId());
        }

        int previous = m.getStock();
        m.setStock(previous - qty);
        m.setLinkedProduct(product);
        m.setUpdatedBy(actor);
        rawMaterialRepository.save(m);
        String note = blankToNull(req.getReason());
        RawMaterialMovement mv = record(m, actor, RawMaterialMovementType.TO_INVENTORY, qty, previous,
                truncate("Pasado a inventario: " + product.getName() + (note != null ? " — " + note : ""), 255), product);
        productService.receiveStock(product, qty,
                truncate("Desde materia prima: " + m.getName() + (note != null ? " — " + note : ""), 255), "MP-" + mv.getId(), actor);
        return m;
    }

    @Transactional
    public void deactivate(Long id, User actor) {
        RawMaterial m = findById(id, actor);
        m.setIsActive(false);
        m.setDeletedBy(actor);
        m.setDeletedAt(LocalDateTime.now());
        rawMaterialRepository.save(m);
    }

    private RawMaterialMovement record(RawMaterial m, User actor, RawMaterialMovementType type, int qty, int previous,
                                       String reason, Product product) {
        RawMaterialMovement mv = new RawMaterialMovement();
        mv.setRawMaterial(m);
        mv.setUser(actor);
        mv.setType(type);
        mv.setQuantity(qty);
        mv.setPreviousStock(previous);
        mv.setNewStock(type == RawMaterialMovementType.IN ? previous + qty : previous - qty);
        mv.setReason(reason);
        mv.setProduct(product);
        return movementRepository.save(mv);
    }

    private void assertInScope(RawMaterial m, User actor) {
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && !scope.equals(m.getTienda().getId())) {
            throw new IllegalArgumentException("Materia prima no encontrada: " + m.getId());
        }
    }

    private void validateBarcodeUnique(String barcode, Long tiendaId, Long excludeId) {
        if (barcode != null && rawMaterialRepository.existsActiveBarcode(barcode, tiendaId, excludeId)) {
            throw new FieldConflictException("barcode", "Ya hay otra materia prima con ese código");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
