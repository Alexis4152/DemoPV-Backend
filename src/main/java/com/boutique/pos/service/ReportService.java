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

/**
 * Genera los datos de reportería y dashboard consumidos por el frontend: resumen de
 * ventas, productos más vendidos, ventas por día, estado de inventario (stock bajo) y
 * movimientos de inventario de un producto.
 *
 * <p>Todas las consultas se acotan a la tienda del actor mediante {@link TenantScope}
 * (o a todas las tiendas si es SUPER_ADMIN). Los rangos de fecha se reciben como
 * {@link LocalDate} inclusivos y se convierten internamente a un rango
 * {@code [start, end)} en {@link LocalDateTime}, sumando un día al {@code to} para
 * incluir completo el último día del rango.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductService productService;
    private final TenantScope tenantScope;

    /**
     * Calcula el resumen de ventas de un periodo: total vendido, número de transacciones
     * y ticket promedio.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return mapa con {@code totalSales}, {@code totalTransactions}, {@code averageTicket},
     *         {@code from} y {@code to}; {@code totalSales} es {@link BigDecimal#ZERO} si
     *         no hay ventas en el periodo
     */
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

    /**
     * Obtiene los productos más vendidos en un periodo, limitado a {@code limit} resultados.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param limit cantidad máxima de productos a devolver
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (producto, cantidad vendida, etc.) según la
     *         proyección de {@code saleItemRepository.topProductsBetween}
     */
    public List<Object[]> topProducts(LocalDate from, LocalDate to, int limit, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleItemRepository.topProductsBetween(start, end, limit, tenantScope.scopeId(actor));
    }

    /**
     * Obtiene el total vendido agrupado por día dentro de un periodo, para graficar
     * la tendencia de ventas en el dashboard.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (fecha, total del día)
     */
    public List<Object[]> salesByDay(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleRepository.salesByDay(start, end, tenantScope.scopeId(actor));
    }

    /**
     * Obtiene los productos con stock por debajo de su mínimo configurado, para la
     * alerta de inventario del dashboard.
     *
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return mapa con la clave {@code lowStockProducts}
     */
    public Map<String, Object> inventoryStatus(User actor) {
        List<Object[]> lowStock = saleRepository.lowStockProducts(tenantScope.scopeId(actor));
        Map<String, Object> map = new HashMap<>();
        map.put("lowStockProducts", lowStock);
        return map;
    }

    /**
     * Lista los movimientos de inventario (entradas, salidas, ventas) de un producto
     * dentro de un periodo.
     *
     * @param productId id del producto a consultar
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param actor usuario que consulta; se valida que el producto pertenezca a su tienda
     * @return movimientos de inventario del producto en el periodo
     * @throws IllegalArgumentException si el producto no existe o no pertenece a la
     *         tienda del actor
     */
    public List<Object[]> movementsByProduct(Long productId, LocalDate from, LocalDate to, User actor) {
        productService.findById(productId, actor); // valida que el producto sea de la tienda del actor
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return movementRepository.findByProductIdAndCreatedAtBetween(productId, start, end);
    }
}
