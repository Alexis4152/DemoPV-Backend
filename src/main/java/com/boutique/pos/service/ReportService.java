package com.boutique.pos.service;

import com.boutique.pos.model.User;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.SaleItemRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * Total vendido y número de ventas agrupado por método de pago dentro de un periodo —
     * útil para cuadrar caja (cuánto entró en efectivo vs. tarjeta vs. transferencia).
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (método de pago, total, número de ventas)
     */
    public List<Object[]> salesByPaymentMethod(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleRepository.salesByPaymentMethod(start, end, tenantScope.scopeId(actor));
    }

    /**
     * Ranking de vendedores/cajeros por monto total vendido dentro de un periodo, limitado
     * a {@code limit} resultados.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param limit cantidad máxima de vendedores a devolver
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (id de usuario, nombre, total vendido, # de ventas)
     */
    public List<Object[]> topSellers(LocalDate from, LocalDate to, int limit, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Pageable pageable = PageRequest.of(0, limit);
        return saleRepository.topSellers(start, end, tenantScope.scopeId(actor), pageable);
    }

    /**
     * Total vendido agrupado por mes dentro de un periodo, pensado para detectar
     * temporadas altas/bajas en rangos que cubren varios meses.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (primer día del mes, total vendido, # de ventas)
     */
    public List<Object[]> salesByMonth(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleRepository.salesByMonth(start, end, tenantScope.scopeId(actor));
    }

    /**
     * Rentabilidad estimada (ingreso menos costo) por producto dentro de un periodo,
     * limitado a {@code limit} resultados, ordenado de mayor a menor margen. Ver la nota
     * sobre el uso del costo actual (no histórico) en {@link SaleItemRepository#topProductsByMargin}.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param limit cantidad máxima de productos a devolver
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (id, nombre, ingreso, costo estimado, margen)
     */
    public List<Object[]> topProductsByMargin(LocalDate from, LocalDate to, int limit, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleItemRepository.topProductsByMargin(start, end, limit, tenantScope.scopeId(actor));
    }

    /**
     * Total vendido por categoría de producto dentro de un periodo, ordenado de mayor a
     * menor monto.
     *
     * @param from fecha inicial (inclusiva)
     * @param to fecha final (inclusiva)
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return filas crudas del query (id de categoría, nombre, cantidad vendida, total vendido)
     */
    public List<Object[]> salesByCategory(LocalDate from, LocalDate to, User actor) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        return saleItemRepository.salesByCategory(start, end, tenantScope.scopeId(actor));
    }

    /**
     * Compara el periodo seleccionado contra el mismo rango de fechas del año anterior
     * (ej. si se pide 01/09/2026 a 30/09/2026, compara contra 01/09/2025 a 30/09/2025), para
     * detectar crecimiento o caída interanual.
     *
     * @param from fecha inicial del periodo actual (inclusiva)
     * @param to fecha final del periodo actual (inclusiva)
     * @param actor usuario que consulta; acota el resultado a su tienda
     * @return mapa con {@code current} y {@code previous} (mismo formato que
     *         {@link #salesSummary}), {@code previousFrom}/{@code previousTo}, y
     *         {@code changePercent} (nulo si el periodo anterior no tuvo ventas, para no
     *         mostrar un porcentaje sin sentido dividiendo entre cero)
     */
    public Map<String, Object> yearOverYearComparison(LocalDate from, LocalDate to, User actor) {
        LocalDate previousFrom = from.minusYears(1);
        LocalDate previousTo = to.minusYears(1);
        Map<String, Object> current = salesSummary(from, to, actor);
        Map<String, Object> previous = salesSummary(previousFrom, previousTo, actor);

        BigDecimal currentTotal = (BigDecimal) current.get("totalSales");
        BigDecimal previousTotal = (BigDecimal) previous.get("totalSales");
        BigDecimal changePercent = previousTotal != null && previousTotal.signum() > 0
                ? currentTotal.subtract(previousTotal)
                        .divide(previousTotal, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : null;

        Map<String, Object> map = new HashMap<>();
        map.put("current", current);
        map.put("previous", previous);
        map.put("previousFrom", previousFrom);
        map.put("previousTo", previousTo);
        map.put("changePercent", changePercent);
        return map;
    }
}
