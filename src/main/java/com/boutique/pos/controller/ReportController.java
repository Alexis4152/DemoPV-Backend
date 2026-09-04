package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ReportPdfService;
import com.boutique.pos.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controlador de reportes, expuesto bajo {@code /api/reports}.
 * <p>
 * Expone consultas agregadas de ventas e inventario (resúmenes, productos más
 * vendidos, ventas por día, estado de inventario y movimientos de un producto)
 * para la tienda del usuario autenticado. La mayoría de los endpoints requiere
 * acceso a la sección {@code REPORTS}; el resumen de ventas también admite el
 * acceso desde {@code DASHBOARD} porque alimenta un widget del panel principal.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;

    // usado también por el widget del Dashboard, por eso admite ambas secciones
    /**
     * Resumen agregado de ventas (totales, tickets, etc.) dentro del rango de
     * fechas indicado. Accesible desde {@code REPORTS} o {@code DASHBOARD}
     * (este último usado por un widget del panel principal).
     *
     * @param from  fecha inicial del rango (inclusive)
     * @param to    fecha final del rango (inclusive)
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/sales-summary")
    @PreAuthorize("@sectionAccess.checkAny('REPORTS', 'DASHBOARD')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> salesSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesSummary(from, to, actor), null));
    }

    /**
     * Lista los productos más vendidos dentro del rango de fechas indicado,
     * limitado a los {@code limit} primeros. Requiere acceso a {@code REPORTS}.
     *
     * @param from  fecha inicial del rango (inclusive)
     * @param to    fecha final del rango (inclusive)
     * @param limit cantidad máxima de productos a devolver (por defecto 10)
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/top-products")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.topProducts(from, to, limit, actor), null));
    }

    /**
     * Devuelve el total de ventas agrupado por día dentro del rango indicado.
     * Requiere acceso a {@code REPORTS}.
     *
     * @param from  fecha inicial del rango (inclusive)
     * @param to    fecha final del rango (inclusive)
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/sales-by-day")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesByDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesByDay(from, to, actor), null));
    }

    /**
     * Devuelve el estado actual del inventario de la tienda (totales, stock
     * bajo, etc.). Requiere acceso a {@code REPORTS}.
     *
     * @param actor usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/inventory-status")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inventoryStatus(@AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.inventoryStatus(actor), null));
    }

    /**
     * Lista los movimientos de inventario (entradas, salidas, ajustes) de un
     * producto específico dentro del rango de fechas indicado. Requiere acceso
     * a {@code REPORTS}.
     *
     * @param productId identificador del producto
     * @param from      fecha inicial del rango (inclusive)
     * @param to        fecha final del rango (inclusive)
     * @param actor     usuario autenticado; determina el filtro por tienda
     */
    @GetMapping("/inventory-movements/{productId}")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> movements(
            @PathVariable Long productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.movementsByProduct(productId, from, to, actor), null));
    }

    /**
     * Total vendido y número de ventas agrupado por método de pago dentro del rango
     * indicado (útil para cuadrar caja). Requiere acceso a {@code REPORTS}.
     */
    @GetMapping("/sales-by-payment-method")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesByPaymentMethod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesByPaymentMethod(from, to, actor), null));
    }

    /**
     * Ranking de vendedores/cajeros por monto total vendido dentro del rango indicado,
     * limitado a los {@code limit} primeros. Requiere acceso a {@code REPORTS}.
     */
    @GetMapping("/top-sellers")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> topSellers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.topSellers(from, to, limit, actor), null));
    }

    /**
     * Total vendido agrupado por mes dentro del rango indicado, para detectar temporadas
     * altas/bajas en rangos que cubren varios meses. Requiere acceso a {@code REPORTS}.
     */
    @GetMapping("/sales-by-month")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesByMonth(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesByMonth(from, to, actor), null));
    }

    /**
     * Rentabilidad estimada (ingreso menos costo actual) por producto dentro del rango
     * indicado, limitado a los {@code limit} primeros por margen. Requiere acceso a
     * {@code REPORTS}.
     */
    @GetMapping("/top-products-by-margin")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> topProductsByMargin(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.topProductsByMargin(from, to, limit, actor), null));
    }

    /**
     * Total vendido por categoría de producto dentro del rango indicado. Requiere acceso
     * a {@code REPORTS}.
     */
    @GetMapping("/sales-by-category")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<List<Object[]>>> salesByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.salesByCategory(from, to, actor), null));
    }

    /**
     * Compara el rango de fechas indicado contra el mismo rango del año anterior, para
     * detectar crecimiento o caída interanual. Requiere acceso a {@code REPORTS}.
     */
    @GetMapping("/year-over-year")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> yearOverYear(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.yearOverYearComparison(from, to, actor), null));
    }

    /**
     * Genera el reporte completo del rango de fechas indicado en PDF (resumen, ventas por
     * método de pago, ranking de vendedores, productos más vendidos y más rentables, ventas
     * por categoría, comparativo interanual y stock bajo), listo para descargarse. Requiere
     * acceso a {@code REPORTS}.
     */
    @GetMapping("/pdf")
    @PreAuthorize("@sectionAccess.check('REPORTS')")
    public ResponseEntity<byte[]> pdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User actor) {
        byte[] pdf = reportPdfService.generate(from, to, actor);
        String filename = "reporte-" + from + "-a-" + to + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
