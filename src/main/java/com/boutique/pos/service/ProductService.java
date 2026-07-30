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

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final InventoryMovementRepository movementRepository;
    private final TenantScope tenantScope;

    public Page<Product> search(String q, Long categoryId, Boolean lowStock, Pageable pageable, User actor) {
        return productRepository.searchActive(q, categoryId, lowStock, tenantScope.scopeId(actor), pageable);
    }

    public List<Product> findAll(User actor) {
        return productRepository.findAllActive(tenantScope.scopeId(actor));
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + id));
    }

    public Product findById(Long id, User actor) {
        Product p = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (p.getTienda() == null || !scope.equals(p.getTienda().getId()))) {
            throw new IllegalArgumentException("Producto no encontrado: " + id);
        }
        return p;
    }

    public Product create(ProductRequest req, User actor) {
        Category cat = categoryService.findById(req.getCategoryId(), actor);

        Product p = new Product();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setBarcode(req.getBarcode());
        p.setPrice(req.getPrice());
        p.setCost(req.getCost());
        p.setStock(req.getStock() != null ? req.getStock() : 0);
        p.setMinStock(req.getMinStock() != null ? req.getMinStock() : 5);
        p.setUnit(req.getUnit() != null ? req.getUnit() : "pieza");
        p.setCategory(cat);
        p.setTienda(actor.getTienda());
        p.setIsActive(true);
        Product saved = productRepository.save(p);

        if (saved.getStock() > 0) {
            recordMovement(saved, actor, MovementType.IN, saved.getStock(), 0, "Stock inicial");
        }
        return saved;
    }

    public Product update(Long id, ProductRequest req, User actor) {
        Product p = findById(id, actor);
        Category cat = categoryService.findById(req.getCategoryId(), actor);

        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setBarcode(req.getBarcode());
        p.setPrice(req.getPrice());
        p.setCost(req.getCost());
        p.setMinStock(req.getMinStock() != null ? req.getMinStock() : 5);
        p.setUnit(req.getUnit() != null ? req.getUnit() : "pieza");
        p.setCategory(cat);
        return productRepository.save(p);
    }

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

    public void deactivate(Long id, User actor) {
        Product p = findById(id, actor);
        p.setIsActive(false);
        productRepository.save(p);
    }

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
