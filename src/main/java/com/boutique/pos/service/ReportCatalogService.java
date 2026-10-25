package com.boutique.pos.service;

import com.boutique.pos.dto.catalog.ReportCatalogDtos.Chart;
import com.boutique.pos.dto.catalog.ReportCatalogDtos.Column;
import com.boutique.pos.dto.catalog.ReportCatalogDtos.Filters;
import com.boutique.pos.dto.catalog.ReportCatalogDtos.Metric;
import com.boutique.pos.dto.catalog.ReportCatalogDtos.Point;
import com.boutique.pos.dto.catalog.ReportCatalogDtos.Report;
import com.boutique.pos.dto.catalog.ReportCatalogDtos.ReportInfo;
import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoStatus;
import com.boutique.pos.model.CashCut;
import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.InventoryMovement;
import com.boutique.pos.model.MovementType;
import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.ApartadoRepository;
import com.boutique.pos.repository.CashCutRepository;
import com.boutique.pos.repository.InventoryMovementRepository;
import com.boutique.pos.repository.SaleItemRepository;
import com.boutique.pos.repository.SaleRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo declarativo de reportes del POS: un solo punto de entrada ({@link #catalog()} +
 * {@link #report(String, Filters, boolean, User)}) que arma cualquiera de los reportes en la
 * misma forma ({@link Report}: indicadores + gráficas + tabla), consumida tanto por la
 * pantalla de "Reportes" como por su exportación (ver {@code ReportCatalogExporter}).
 *
 * <p>Mismo patrón que el módulo de reportes de {@code nexora-hotel-backend} (catálogo +
 * endpoint genérico por tipo), adaptado a los datos propios del POS: cada reporte de aquí
 * consulta ventas, apartados, inventario o cortes de caja — nunca reservaciones de hotel.
 * A diferencia del {@link ReportService} original (que expone un endpoint fijo por cada
 * consulta, usado por el widget del Dashboard y por {@link ReportPdfService}), este
 * servicio es el que alimenta la pantalla de "Reportes" completa; ambos conviven porque
 * resuelven necesidades distintas.</p>
 *
 * <p>Las cifras de "indicadores" (metrics) se calculan sobre las filas efectivamente
 * traídas (hasta {@link #SCREEN_ROWS} en pantalla, {@link #EXPORT_ROWS} al exportar), nunca
 * sobre la tabla completa sin límite — si el periodo tiene más filas de las traídas, el
 * reporte queda marcado {@code truncated} y se agrega una nota aclarando que hay que
 * exportar para ver el total real, en vez de mostrar una cifra que parece completa sin
 * serlo.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportCatalogService {

    private static final int SCREEN_ROWS = 300;
    private static final int EXPORT_ROWS = 5000;
    private static final String CURRENCY = "MXN";
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    // Tan amplio que en la práctica cubre "siempre", para los reportes "snapshot" (situación
    // actual) que reusan un query con rango de fechas pero no deben filtrar por fecha.
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    private static final Map<SaleStatus, String> SALE_STATUS_LABEL = Map.of(
            SaleStatus.COMPLETED, "Completada", SaleStatus.CANCELLED, "Cancelada");
    private static final Map<PaymentMethod, String> METHOD_LABEL = Map.of(
            PaymentMethod.CASH, "Efectivo", PaymentMethod.CARD, "Tarjeta", PaymentMethod.TRANSFER, "Transferencia");
    private static final Map<ApartadoStatus, String> APARTADO_STATUS_LABEL = Map.of(
            ApartadoStatus.PENDING, "Pendiente", ApartadoStatus.ACTIVE, "Activo",
            ApartadoStatus.COMPLETED, "Completado", ApartadoStatus.CANCELLED, "Cancelado",
            ApartadoStatus.EXPIRED, "Vencido");
    private static final Map<MovementType, String> MOVEMENT_LABEL = Map.of(
            MovementType.IN, "Entrada", MovementType.OUT, "Salida",
            MovementType.ADJUSTMENT, "Ajuste", MovementType.SALE, "Venta (salida)");
    private static final Map<CashCutStatus, String> CASHCUT_STATUS_LABEL = Map.of(
            CashCutStatus.OPEN, "Abierto", CashCutStatus.CLOSED, "Cerrado");

    /** Catálogo: tipo → (título, descripción, grupo, ¿es foto actual?, filtros que acepta). */
    private static final List<ReportInfo> CATALOG = List.of(
            new ReportInfo("ventas", "Ventas", "Historial de ventas por fecha, con su método de pago y estado.",
                    "Operación", false, List.of("status", "method")),
            new ReportInfo("top-productos", "Productos más vendidos", "Cantidad y monto vendido de cada producto en el periodo.",
                    "Operación", false, List.of()),
            new ReportInfo("ventas-categoria", "Ventas por categoría", "Total vendido agrupado por categoría de producto.",
                    "Operación", false, List.of()),
            new ReportInfo("apartados", "Apartados", "Historial de apartados por fecha de solicitud y estado.",
                    "Operación", false, List.of("status")),
            new ReportInfo("apartados-pendientes", "Apartados pendientes", "Solicitudes de apartado sin confirmar en este momento.",
                    "Operación", true, List.of()),
            new ReportInfo("movimientos-inventario", "Movimientos de inventario", "Entradas, salidas, ventas y ajustes de stock.",
                    "Operación", false, List.of("movementType")),
            new ReportInfo("stock-bajo", "Stock bajo", "Productos activos con existencia igual o menor a su mínimo configurado.",
                    "Operación", true, List.of()),
            new ReportInfo("ingresos", "Ingresos por periodo", "Total vendido día por día dentro del rango elegido.",
                    "Finanzas", false, List.of()),
            new ReportInfo("pagos-metodo", "Pagos por método", "Total cobrado y número de ventas por método de pago — útil para cuadrar caja.",
                    "Finanzas", false, List.of()),
            new ReportInfo("vendedores", "Ranking de vendedores", "Total vendido por cada cajero/vendedor dentro del periodo.",
                    "Finanzas", false, List.of()),
            new ReportInfo("rentabilidad", "Rentabilidad de productos", "Ingreso menos costo estimado por producto.",
                    "Finanzas", false, List.of()),
            new ReportInfo("comparativo-anual", "Comparativo anual", "El periodo elegido contra el mismo rango del año anterior.",
                    "Finanzas", false, List.of()),
            new ReportInfo("cortes-caja", "Cortes de caja", "Historial de cortes de caja, abiertos y cerrados.",
                    "Finanzas", false, List.of("status")));

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ApartadoRepository apartadoRepository;
    private final CashCutRepository cashCutRepository;
    private final InventoryMovementRepository movementRepository;
    private final TenantScope tenantScope;

    public List<ReportInfo> catalog() {
        return CATALOG;
    }

    private static ReportInfo info(String type) {
        return CATALOG.stream().filter(i -> i.type().equals(type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Reporte no encontrado: " + type));
    }

    /** Contexto resuelto de una consulta: tienda del actor, rango de fechas normalizado y
     *  cuántas filas traer (pantalla vs. exportación). */
    private record Ctx(Long tiendaId, LocalDate from, LocalDate to, LocalDateTime start, LocalDateTime end,
                        int limit, boolean forExport) {
    }

    public Report report(String type, Filters raw, boolean forExport, User actor) {
        ReportInfo meta = info(type);
        LocalDate today = LocalDate.now();
        LocalDate from = raw.from() != null ? raw.from() : today.withDayOfMonth(1);
        LocalDate to = raw.to() != null ? raw.to() : today;
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial");
        }
        Ctx c = new Ctx(tenantScope.scopeId(actor), from, to, from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                forExport ? EXPORT_ROWS : SCREEN_ROWS, forExport);
        Report r = switch (type) {
            case "ventas" -> ventas(c, raw);
            case "top-productos" -> topProductos(c);
            case "ventas-categoria" -> ventasPorCategoria(c);
            case "apartados" -> apartados(c, raw);
            case "apartados-pendientes" -> apartadosPendientes(c);
            case "movimientos-inventario" -> movimientosInventario(c, raw);
            case "stock-bajo" -> stockBajo(c);
            case "ingresos" -> ingresos(c);
            case "pagos-metodo" -> pagosPorMetodo(c);
            case "vendedores" -> vendedores(c);
            case "rentabilidad" -> rentabilidad(c);
            case "comparativo-anual" -> comparativoAnual(c);
            case "cortes-caja" -> cortesCaja(c, raw);
            default -> throw new IllegalArgumentException("Reporte no encontrado: " + type);
        };
        return withMeta(r, meta);
    }

    private Report withMeta(Report r, ReportInfo meta) {
        return new Report(r.type(), meta.title(), r.basis(), r.from(), r.to(), CURRENCY, Instant.now(),
                r.metrics(), r.charts(), r.columns(), r.rows(), r.totalRows(), r.truncated(), r.notes());
    }

    // ── Ventas ────────────────────────────────────────────────────────────────────────────

    private Report ventas(Ctx c, Filters f) {
        SaleStatus status = enumOrNull(f.status(), SaleStatus.class);
        PaymentMethod method = enumOrNull(f.method(), PaymentMethod.class);
        Page<Sale> page = saleRepository.search(c.tiendaId(), c.start(), c.end(), null, method, status,
                PageRequest.of(0, c.limit(), Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Column> columns = List.of(
                new Column("id", "Folio", "number"),
                new Column("fecha", "Fecha", "datetime"),
                new Column("cliente", "Cliente", "text"),
                new Column("vendedor", "Vendedor", "text"),
                new Column("metodo", "Método", "text"),
                new Column("estado", "Estado", "text"),
                new Column("subtotal", "Subtotal", "money"),
                new Column("descuento", "Descuento", "money"),
                new Column("total", "Total", "money"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        for (Sale s : page.getContent()) {
            rows.add(row(
                    "id", s.getFolio(),
                    "fecha", iso(s.getCreatedAt()),
                    "cliente", blankToDash(s.getCustomerName()),
                    "vendedor", s.getUser() != null ? s.getUser().getName() : "",
                    "metodo", METHOD_LABEL.getOrDefault(s.getPaymentMethod(), ""),
                    "estado", SALE_STATUS_LABEL.getOrDefault(s.getStatus(), ""),
                    "subtotal", s.getSubtotal(),
                    "descuento", s.getDiscount(),
                    "total", s.getTotal()));
            totalSum = totalSum.add(s.getTotal());
        }
        List<Metric> metrics = List.of(
                metric("count", "Ventas", page.getContent().size(), "count", null),
                metric("total", "Total vendido", totalSum, "money", null));
        return base(c, columns, rows, (int) page.getTotalElements(), metrics, List.of(dailyChart(c, "Ventas por día")));
    }

    private Report topProductos(Ctx c) {
        List<Object[]> rowsRaw = saleItemRepository.topProductsBetween(c.start(), c.end(), c.limit(), c.tiendaId());
        List<Column> columns = List.of(
                new Column("producto", "Producto", "text"),
                new Column("cantidad", "Cantidad vendida", "number"),
                new Column("total", "Total vendido", "money"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        List<Point> points = new ArrayList<>();
        for (Object[] r : rowsRaw) {
            String name = String.valueOf(r[1]);
            BigDecimal qty = toDecimal(r[2]);
            BigDecimal total = toDecimal(r[3]);
            rows.add(row("producto", name, "cantidad", qty, "total", total));
            totalSum = totalSum.add(total);
            points.add(new Point(truncate(name, 20), List.of(total)));
        }
        List<Metric> metrics = List.of(metric("total", "Total vendido (top productos)", totalSum, "money", null));
        Chart chart = new Chart("top", "Productos más vendidos", "bar", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report ventasPorCategoria(Ctx c) {
        List<Object[]> rowsRaw = saleItemRepository.salesByCategory(c.start(), c.end(), c.tiendaId());
        List<Column> columns = List.of(
                new Column("categoria", "Categoría", "text"),
                new Column("cantidad", "Cantidad vendida", "number"),
                new Column("total", "Total vendido", "money"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        List<Point> points = new ArrayList<>();
        for (Object[] r : rowsRaw) {
            String name = String.valueOf(r[1]);
            BigDecimal qty = toDecimal(r[2]);
            BigDecimal total = toDecimal(r[3]);
            rows.add(row("categoria", name, "cantidad", qty, "total", total));
            totalSum = totalSum.add(total);
            points.add(new Point(truncate(name, 20), List.of(total)));
        }
        List<Metric> metrics = List.of(metric("total", "Total vendido", totalSum, "money", null));
        Chart chart = new Chart("cat", "Ventas por categoría", "bar", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    // ── Apartados ─────────────────────────────────────────────────────────────────────────

    private Report apartados(Ctx c, Filters f) {
        ApartadoStatus status = enumOrNull(f.status(), ApartadoStatus.class);
        Page<Apartado> page = apartadoRepository.search(c.tiendaId(), status, null, null, c.start(), c.end(), null,
                PageRequest.of(0, c.limit(), Sort.by(Sort.Direction.DESC, "requestedAt")));
        return apartadosReport(c, page);
    }

    private Report apartadosPendientes(Ctx c) {
        // "snapshot": todos los PENDING ahora mismo, sin filtrar por fecha de solicitud.
        Page<Apartado> page = apartadoRepository.search(c.tiendaId(), ApartadoStatus.PENDING, null, null, MIN_DATE, MAX_DATE,
                null, PageRequest.of(0, c.limit(), Sort.by(Sort.Direction.DESC, "requestedAt")));
        return apartadosReport(c, page);
    }

    private Report apartadosReport(Ctx c, Page<Apartado> page) {
        List<Column> columns = List.of(
                new Column("id", "Folio", "number"),
                new Column("fecha", "Solicitado", "datetime"),
                new Column("cliente", "Cliente", "text"),
                new Column("telefono", "Teléfono", "text"),
                new Column("estado", "Estado", "text"),
                new Column("total", "Total", "money"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        for (Apartado a : page.getContent()) {
            rows.add(row(
                    "id", a.getFolio(),
                    "fecha", iso(a.getRequestedAt()),
                    "cliente", blankToDash(a.getCustomerName()),
                    "telefono", blankToDash(a.getCustomerPhone()),
                    "estado", APARTADO_STATUS_LABEL.getOrDefault(a.getStatus(), ""),
                    "total", a.getTotal()));
            totalSum = totalSum.add(a.getTotal() != null ? a.getTotal() : BigDecimal.ZERO);
        }
        List<Metric> metrics = List.of(
                metric("count", "Apartados", page.getContent().size(), "count", null),
                metric("total", "Valor total", totalSum, "money", "Valor de los apartados mostrados, cobrado o no."));
        return base(c, columns, rows, (int) page.getTotalElements(), metrics, List.of());
    }

    // ── Inventario ────────────────────────────────────────────────────────────────────────

    private Report movimientosInventario(Ctx c, Filters f) {
        MovementType type = enumOrNull(f.movementType(), MovementType.class);
        Page<InventoryMovement> page = movementRepository.search(c.tiendaId(), c.start(), c.end(), type,
                PageRequest.of(0, c.limit()));
        List<Column> columns = List.of(
                new Column("fecha", "Fecha", "datetime"),
                new Column("producto", "Producto", "text"),
                new Column("tipo", "Tipo", "text"),
                new Column("cantidad", "Cantidad", "number"),
                new Column("stockAnterior", "Stock anterior", "number"),
                new Column("stockNuevo", "Stock nuevo", "number"),
                new Column("usuario", "Usuario", "text"),
                new Column("motivo", "Motivo", "text"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InventoryMovement m : page.getContent()) {
            rows.add(row(
                    "fecha", iso(m.getCreatedAt()),
                    "producto", m.getProduct() != null ? m.getProduct().getName() : "",
                    "tipo", MOVEMENT_LABEL.getOrDefault(m.getType(), ""),
                    "cantidad", m.getQuantity(),
                    "stockAnterior", m.getPreviousStock(),
                    "stockNuevo", m.getNewStock(),
                    "usuario", m.getUser() != null ? m.getUser().getName() : "",
                    "motivo", blankToDash(m.getReason())));
        }
        List<Metric> metrics = List.of(metric("count", "Movimientos", page.getContent().size(), "count", null));
        return base(c, columns, rows, (int) page.getTotalElements(), metrics, List.of());
    }

    private Report stockBajo(Ctx c) {
        List<Object[]> rowsRaw = saleRepository.lowStockProducts(c.tiendaId());
        List<Column> columns = List.of(
                new Column("producto", "Producto", "text"),
                new Column("stock", "Existencia", "number"),
                new Column("minimo", "Mínimo configurado", "number"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : rowsRaw) {
            rows.add(row("producto", r[1], "stock", r[2], "minimo", r[3]));
        }
        List<Metric> metrics = List.of(metric("count", "Productos en stock bajo", rows.size(), "count", null));
        return base(c, columns, rows, rows.size(), metrics, List.of());
    }

    // ── Finanzas ──────────────────────────────────────────────────────────────────────────

    private Report ingresos(Ctx c) {
        List<Object[]> rowsRaw = saleRepository.salesByDay(c.start(), c.end(), c.tiendaId());
        List<Column> columns = List.of(
                new Column("fecha", "Fecha", "date"),
                new Column("total", "Total vendido", "money"),
                new Column("ventas", "Número de ventas", "count"));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        long countSum = 0;
        for (Object[] r : rowsRaw) {
            String date = String.valueOf(r[0]);
            BigDecimal total = toDecimal(r[1]);
            long count = ((Number) r[2]).longValue();
            rows.add(row("fecha", date, "total", total, "ventas", count));
            points.add(new Point(date, List.of(total)));
            totalSum = totalSum.add(total);
            countSum += count;
        }
        List<Metric> metrics = List.of(
                metric("total", "Total del periodo", totalSum, "money", null),
                metric("count", "Transacciones", countSum, "count", null),
                metric("avg", "Ticket promedio", countSum > 0 ? totalSum.divide(BigDecimal.valueOf(countSum), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO, "money", null));
        Chart chart = new Chart("income", "Ingresos por día", "line", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report pagosPorMetodo(Ctx c) {
        List<Object[]> rowsRaw = saleRepository.salesByPaymentMethod(c.start(), c.end(), c.tiendaId());
        List<Column> columns = List.of(
                new Column("metodo", "Método", "text"),
                new Column("total", "Total cobrado", "money"),
                new Column("ventas", "Número de ventas", "count"));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        for (Object[] r : rowsRaw) {
            PaymentMethod m = (PaymentMethod) r[0];
            BigDecimal total = toDecimal(r[1]);
            long count = ((Number) r[2]).longValue();
            String label = METHOD_LABEL.getOrDefault(m, String.valueOf(m));
            rows.add(row("metodo", label, "total", total, "ventas", count));
            points.add(new Point(label, List.of(total)));
            totalSum = totalSum.add(total);
        }
        List<Metric> metrics = List.of(metric("total", "Total cobrado", totalSum, "money", null));
        Chart chart = new Chart("method", "Pagos por método", "bar", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report vendedores(Ctx c) {
        List<Object[]> rowsRaw = saleRepository.topSellers(c.start(), c.end(), c.tiendaId(), PageRequest.of(0, c.limit()));
        List<Column> columns = List.of(
                new Column("vendedor", "Vendedor", "text"),
                new Column("total", "Total vendido", "money"),
                new Column("ventas", "Número de ventas", "count"));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        BigDecimal totalSum = BigDecimal.ZERO;
        for (Object[] r : rowsRaw) {
            String name = String.valueOf(r[1]);
            BigDecimal total = toDecimal(r[2]);
            long count = ((Number) r[3]).longValue();
            rows.add(row("vendedor", name, "total", total, "ventas", count));
            points.add(new Point(name, List.of(total)));
            totalSum = totalSum.add(total);
        }
        List<Metric> metrics = List.of(metric("total", "Total vendido", totalSum, "money", null));
        Chart chart = new Chart("sellers", "Ranking de vendedores", "bar", "money", List.of("Total"), points);
        return base(c, columns, rows, rows.size(), metrics, rows.isEmpty() ? List.of() : List.of(chart));
    }

    private Report rentabilidad(Ctx c) {
        List<Object[]> rowsRaw = saleItemRepository.topProductsByMargin(c.start(), c.end(), c.limit(), c.tiendaId());
        List<Column> columns = List.of(
                new Column("producto", "Producto", "text"),
                new Column("ingreso", "Ingreso", "money"),
                new Column("costo", "Costo estimado", "money"),
                new Column("margen", "Margen", "money"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal marginSum = BigDecimal.ZERO;
        List<Point> points = new ArrayList<>();
        for (Object[] r : rowsRaw) {
            String name = String.valueOf(r[1]);
            BigDecimal income = toDecimal(r[2]);
            BigDecimal cost = toDecimal(r[3]);
            BigDecimal margin = toDecimal(r[4]);
            rows.add(row("producto", name, "ingreso", income, "costo", cost, "margen", margin));
            marginSum = marginSum.add(margin);
            points.add(new Point(truncate(name, 20), List.of(margin)));
        }
        List<Metric> metrics = List.of(metric("margin", "Margen total (top productos)", marginSum, "money",
                "El costo usado es el actual del producto, no el histórico de cada venta."));
        Chart chart = new Chart("margin", "Rentabilidad por producto", "bar", "money", List.of("Margen"), points);
        List<String> notes = List.of("El costo usado es el costo ACTUAL del producto en el catálogo, no el que tenía al momento de cada venta.");
        return new Report("rentabilidad", null, "Fecha de venta", c.from(), c.to(), CURRENCY, null,
                metrics, rows.isEmpty() ? List.of() : List.of(chart), columns, rows, rows.size(), false, notes);
    }

    private Report comparativoAnual(Ctx c) {
        LocalDate prevFrom = c.from().minusYears(1);
        LocalDate prevTo = c.to().minusYears(1);
        LocalDateTime prevStart = prevFrom.atStartOfDay();
        LocalDateTime prevEnd = prevTo.plusDays(1).atStartOfDay();

        BigDecimal curTotal = nz(saleRepository.totalBetween(c.start(), c.end(), c.tiendaId()));
        long curCount = saleRepository.countBetween(c.start(), c.end(), c.tiendaId());
        BigDecimal prevTotal = nz(saleRepository.totalBetween(prevStart, prevEnd, c.tiendaId()));
        long prevCount = saleRepository.countBetween(prevStart, prevEnd, c.tiendaId());

        BigDecimal changePercent = prevTotal.signum() > 0
                ? curTotal.subtract(prevTotal).divide(prevTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : null;

        List<Column> columns = List.of(
                new Column("periodo", "Periodo", "text"),
                new Column("rango", "Rango", "text"),
                new Column("total", "Total vendido", "money"),
                new Column("ventas", "Número de ventas", "count"));
        List<Map<String, Object>> rows = List.of(
                row("periodo", "Periodo actual", "rango", c.from() + " – " + c.to(), "total", curTotal, "ventas", curCount),
                row("periodo", "Mismo rango, año anterior", "rango", prevFrom + " – " + prevTo, "total", prevTotal, "ventas", prevCount));

        List<Metric> metrics = new ArrayList<>(List.of(
                metric("current", "Periodo actual", curTotal, "money", null),
                metric("previous", "Año anterior", prevTotal, "money", null)));
        metrics.add(metric("change", "Variación", changePercent, "percent",
                changePercent == null ? "El año anterior no tuvo ventas en este rango." : null));

        return base(c, columns, rows, rows.size(), metrics, List.of());
    }

    private Report cortesCaja(Ctx c, Filters f) {
        CashCutStatus status = enumOrNull(f.status(), CashCutStatus.class);
        // null, null: sin filtro de cajero ni de estado del arqueo (CashCutRepository#search).
        Page<CashCut> page = cashCutRepository.search(c.tiendaId(), c.start(), c.end(), status, null, null,
                PageRequest.of(0, c.limit(), Sort.by(Sort.Direction.DESC, "openedAt")));
        List<Column> columns = List.of(
                new Column("id", "Folio", "number"),
                new Column("cajero", "Cajero", "text"),
                new Column("apertura", "Apertura", "datetime"),
                new Column("cierre", "Cierre", "datetime"),
                new Column("estado", "Estado", "text"),
                new Column("fondo", "Fondo inicial", "money"),
                new Column("ventas", "Ventas totales", "money"),
                new Column("efectivo", "Ventas en efectivo", "money"),
                new Column("diferencia", "Diferencia de caja", "money"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal salesSum = BigDecimal.ZERO;
        for (CashCut cut : page.getContent()) {
            rows.add(row(
                    "id", cut.getId(),
                    "cajero", cut.getUser() != null ? cut.getUser().getName() : "",
                    "apertura", iso(cut.getOpenedAt()),
                    "cierre", cut.getClosedAt() != null ? iso(cut.getClosedAt()) : null,
                    "estado", CASHCUT_STATUS_LABEL.getOrDefault(cut.getStatus(), ""),
                    "fondo", cut.getOpeningAmount(),
                    "ventas", cut.getTotalSales(),
                    "efectivo", cut.getCashSales(),
                    "diferencia", cut.getCashDifference()));
            salesSum = salesSum.add(cut.getTotalSales() != null ? cut.getTotalSales() : BigDecimal.ZERO);
        }
        List<Metric> metrics = List.of(
                metric("count", "Cortes", page.getContent().size(), "count", null),
                metric("total", "Ventas totales", salesSum, "money", null));
        return base(c, columns, rows, (int) page.getTotalElements(), metrics, List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────

    private Chart dailyChart(Ctx c, String title) {
        List<Object[]> rowsRaw = saleRepository.salesByDay(c.start(), c.end(), c.tiendaId());
        List<Point> points = new ArrayList<>();
        for (Object[] r : rowsRaw) {
            points.add(new Point(String.valueOf(r[0]), List.of(toDecimal(r[1]))));
        }
        return new Chart("daily", title, "line", "money", List.of("Total"), points);
    }

    private Report base(Ctx c, List<Column> columns, List<Map<String, Object>> rows, int totalRows,
                         List<Metric> metrics, List<Chart> charts) {
        boolean truncated = rows.size() < totalRows;
        List<String> notes = truncated
                ? List.of("Se muestran " + rows.size() + " de " + totalRows + " registros. Exporta para ver todos y sus totales exactos.")
                : List.of();
        return new Report(null, null, "Fecha", c.from(), c.to(), CURRENCY, null, metrics, charts, columns, rows,
                totalRows, truncated, notes);
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Metric metric(String key, String label, Object value, String format, String hint) {
        return new Metric(key, label, value, format, hint);
    }

    private static <E extends Enum<E>> E enumOrNull(String value, Class<E> type) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Filtro no válido: " + value);
        }
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal b) {
        return b != null ? b : BigDecimal.ZERO;
    }

    private static String iso(LocalDateTime dt) {
        return dt != null ? dt.format(DATETIME) : null;
    }

    private static String blankToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
