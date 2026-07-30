package com.boutique.pos.service;

import com.boutique.pos.model.User;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.SaleItemRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductService productService;
    private final TenantScope tenantScope;

    public Map<String, Object> salesSummary(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Long tiendaId = tenantScope.scopeId(actor);

        BigDecimal total = saleRepository.totalBetween(start, end, tiendaId);
        long count = saleRepository.countBetween(start, end, tiendaId);

        Map<String, Object> map = new HashMap<>();
        map.put("totalSales", total != null ? total : BigDecimal.ZERO);
        map.put("totalTransactions", count);
        map.put("averageTicket", count > 0 && total != null
                ? total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        map.put("from", from);
        map.put("to", to);
        return map;
    }

    public List<Object[]> topProducts(LocalDate from, LocalDate to, int limit, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleItemRepository.topProductsBetween(start, end, limit, tenantScope.scopeId(actor));
    }

    public List<Object[]> salesByDay(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleRepository.salesByDay(start, end, tenantScope.scopeId(actor));
    }

    public Map<String, Object> inventoryStatus(User actor) {
        List<Object[]> lowStock = saleRepository.lowStockProducts(tenantScope.scopeId(actor));
        Map<String, Object> map = new HashMap<>();
        map.put("lowStockProducts", lowStock);
        return map;
    }

    public List<Object[]> movementsByProduct(Long productId, LocalDate from, LocalDate to, User actor) {
        productService.findById(productId, actor); // valida que el producto sea de la tienda del actor
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return movementRepository.findByProductIdAndCreatedAtBetween(productId, start, end);
    }
}
