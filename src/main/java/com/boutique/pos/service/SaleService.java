package com.boutique.pos.service;

import com.boutique.pos.dto.SaleItemRequest;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.model.*;
import com.boutique.pos.repository.*;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final SaleItemRepository saleItemRepository;
    private final CashCutRepository cashCutRepository;
    private final InventoryMovementRepository movementRepository;
    private final EmailService emailService;
    private final TenantScope tenantScope;

    // BETWEEN siempre necesita las dos fechas: cuando el filtro viene vacío, Postgres no
    // logra inferir el tipo de un parámetro timestamp nulo (ni con CAST), así que en vez
    // de mandar null se usa un rango que cubre todo el historial.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    public Page<Sale> findAll(LocalDateTime from, LocalDateTime to, String customerName,
                               PaymentMethod paymentMethod, SaleStatus status, Pageable pageable, User actor) {
        Long scope = tenantScope.scopeId(actor);
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return saleRepository.search(scope, effectiveFrom, effectiveTo, customerName, paymentMethod, status, pageable);
    }

    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + id));
    }

    public Sale findById(Long id, User actor) {
        Sale s = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (s.getTienda() == null || !scope.equals(s.getTienda().getId()))) {
            throw new IllegalArgumentException("Venta no encontrada: " + id);
        }
        return s;
    }

    @Transactional
    public Sale create(SaleRequest req, User actor) {
        // cada cajero/vendedor puede tener su propio corte abierto en simultáneo con
        // los de sus compañeros de tienda, así que la venta se pega al SUYO, no a
        // "el" corte abierto de la tienda (ya no existe tal cosa).
        CashCut openCut = cashCutRepository.findFirstByUserIdAndStatus(actor.getId(), CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de registrar ventas"));

        Sale sale = new Sale();
        sale.setUser(actor);
        sale.setCashCut(openCut);
        sale.setCustomerName(req.getCustomerName());
        sale.setCustomerEmail(req.getCustomerEmail());
        sale.setPaymentMethod(req.getPaymentMethod());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes(req.getNotes());
        sale.setTienda(actor.getTienda());

        List<SaleItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequest ir : req.getItems()) {
            Product product = productService.findById(ir.getProductId(), actor);

            if (!product.getIsActive()) throw new IllegalStateException("Producto inactivo: " + product.getName());

            BigDecimal qty = ir.getQuantity();
            int qtyInt = qty.intValue();
            if (product.getStock() < qtyInt) {
                throw new IllegalStateException("Stock insuficiente para " + product.getName()
                        + " (disponible: " + product.getStock() + ")");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal discount = ir.getDiscount() != null ? ir.getDiscount() : BigDecimal.ZERO;
            BigDecimal itemSubtotal = unitPrice.multiply(qty).subtract(discount);

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(qty);
            item.setUnitPrice(unitPrice);
            item.setDiscount(discount);
            item.setSubtotal(itemSubtotal);
            items.add(item);

            int previous = product.getStock();
            product.setStock(previous - qtyInt);
            productRepository.save(product);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(product);
            mv.setUser(actor);
            mv.setType(MovementType.SALE);
            mv.setQuantity(qtyInt);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous - qtyInt);
            mv.setReason("Venta");
            movementRepository.save(mv);

            subtotal = subtotal.add(itemSubtotal);
        }

        BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;
        BigDecimal tax = req.getTax() != null ? req.getTax() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(tax);

        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTax(tax);
        sale.setTotal(total);
        sale.setItems(items);

        Sale saved = saleRepository.save(sale);

        if (req.getCustomerEmail() != null && !req.getCustomerEmail().isBlank()) {
            emailService.sendTicketEmail(saved, req.getCustomerEmail());
        }

        return saved;
    }

    @Transactional
    public Sale cancel(Long id, User actor) {
        Sale sale = findById(id, actor);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new IllegalStateException("La venta ya está cancelada");
        }
        for (SaleItem item : sale.getItems()) {
            Product p = item.getProduct();
            int qty = item.getQuantity().intValue();
            int previous = p.getStock();
            p.setStock(previous + qty);
            productRepository.save(p);

            InventoryMovement mv = new InventoryMovement();
            mv.setProduct(p);
            mv.setUser(sale.getUser());
            mv.setType(MovementType.IN);
            mv.setQuantity(qty);
            mv.setPreviousStock(previous);
            mv.setNewStock(previous + qty);
            mv.setReason("Cancelación de venta #" + id);
            movementRepository.save(mv);
        }
        sale.setStatus(SaleStatus.CANCELLED);
        return saleRepository.save(sale);
    }
}
