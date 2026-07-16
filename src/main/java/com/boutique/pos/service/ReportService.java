package com.boutique.pos.service;

import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.SaleItemRepository;
import com.boutique.pos.repository.SaleRepository;
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

    public Map<String, Object> salesSummary(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        BigDecimal total = saleRepository.totalBetween(start, end);
        long count = saleRepository.countBetween(start, end);

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

    public List<Object[]> topProducts(LocalDate from, LocalDate to, int limit) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleItemRepository.topProductsBetween(start, end, limit);
    }

    public List<Object[]> salesByDay(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleRepository.salesByDay(start, end);
    }

    public Map<String, Object> inventoryStatus() {
        List<Object[]> lowStock = saleRepository.lowStockProducts();
        Map<String, Object> map = new HashMap<>();
        map.put("lowStockProducts", lowStock);
        return map;
    }

    public List<Object[]> movementsByProduct(Long productId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return movementRepository.findByProductIdAndCreatedAtBetween(productId, start, end);
    }
}
