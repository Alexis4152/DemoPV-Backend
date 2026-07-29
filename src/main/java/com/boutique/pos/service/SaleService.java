package com.boutique.pos.service;

import com.boutique.pos.dto.SaleItemRequest;
import com.boutique.pos.dto.SaleRequest;
import com.boutique.pos.model.*;
import com.boutique.pos.repository.*;
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
    private final SaleItemRepository saleItemRepository;
    private final CashCutRepository cashCutRepository;
    private final InventoryMovementRepository movementRepository;
    private final EmailService emailService;

    public Page<Sale> findAll(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        if (from != null && to != null) {
            return saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageable);
        }
        return saleRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Sale findById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada: " + id));
    }

    @Transactional
    public Sale create(SaleRequest req, User actor) {
        CashCut openCut = cashCutRepository.findFirstByStatus(CashCutStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Debes abrir un corte de caja antes de registrar ventas"));

        Sale sale = new Sale();
        sale.setUser(actor);
        sale.setCashCut(openCut);
        sale.setCustomerName(req.getCustomerName());
        sale.setCustomerEmail(req.getCustomerEmail());
        sale.setPaymentMethod(req.getPaymentMethod());
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setNotes(req.getNotes());

        List<SaleItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequest ir : req.getItems()) {
            Product product = productRepository.findById(ir.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + ir.getProductId()));

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
    public Sale cancel(Long id) {
        Sale sale = findById(id);
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
