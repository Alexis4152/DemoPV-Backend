package com.boutique.pos.service;

import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.security.TenantScope;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Genera el reporte de ventas e inventario de un rango de fechas en PDF, con los mismos
 * datos que se muestran en la pantalla de "Reportes" del frontend, pero en tablas (no
 * gráficas) — pensado para descargarse, imprimirse o archivarse.
 *
 * <p>Reusa exactamente las mismas consultas que alimentan la pantalla ({@link
 * ReportService}), así que el PDF siempre coincide con lo que el usuario ve en pantalla
 * para el mismo rango de fechas. Usa OpenPDF (paquete {@code com.lowagie.text}), igual que
 * {@link TicketPdfService}.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "MX"));
    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private static final Color GRAY_FILL = new Color(238, 238, 238);

    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(90, 90, 90));
    private static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

    private final ReportService reportService;
    private final TenantScope tenantScope;

    /**
     * Genera el PDF completo del reporte para el rango {@code [from, to]} de la tienda del
     * actor: resumen, comparativo interanual, ventas por día/mes/método de pago,
     * productos más vendidos y más rentables, ventas por categoría, ranking de vendedores
     * y estado actual de stock bajo.
     *
     * @param from  fecha inicial (inclusiva)
     * @param to    fecha final (inclusiva)
     * @param actor usuario que solicita el reporte; determina la tienda y el alcance de
     *              todas las consultas (vía {@link ReportService})
     * @return el PDF generado, listo para descargarse
     * @throws IllegalStateException si OpenPDF falla al construir el documento
     */
    public byte[] generate(LocalDate from, LocalDate to, User actor) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // tiendaForWrite (a pesar del nombre, pensado para escritura) también sirve
            // aquí de lectura: es "la tienda que el actor representa ahora mismo" — para
            // un SUPER_ADMIN, la que eligió actuar, en vez de siempre null.
            Tienda tienda = tenantScope.tiendaForWrite(actor);
            document.add(paragraph(tienda != null ? tienda.getName() : "Punto de Venta Demo", FONT_TITLE, Element.ALIGN_LEFT, 0, 2));
            document.add(paragraph("Reporte de ventas: " + DAY_FMT.format(from) + " al " + DAY_FMT.format(to), FONT_SUBTITLE, Element.ALIGN_LEFT, 0, 0));
            document.add(paragraph("Generado el " + DAY_FMT.format(LocalDate.now()), FONT_SUBTITLE, Element.ALIGN_LEFT, 0, 14));

            addSummarySection(document, from, to, actor);
            addYearOverYearSection(document, from, to, actor);
            addSalesByDaySection(document, from, to, actor);
            addSalesByMonthSection(document, from, to, actor);
            addPaymentMethodSection(document, from, to, actor);
            addTopSellersSection(document, from, to, actor);
            addTopProductsSection(document, from, to, actor);
            addMarginSection(document, from, to, actor);
            addCategorySection(document, from, to, actor);
            addLowStockSection(document, actor);

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el reporte en PDF", e);
        }
        return out.toByteArray();
    }

    private void addSummarySection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        Map<String, Object> summary = reportService.salesSummary(from, to, actor);
        document.add(sectionTitle("Resumen del periodo"));
        PdfPTable table = new PdfPTable(new float[]{2, 1});
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addRow(table, "Total vendido", money(summary.get("totalSales")));
        addRow(table, "Transacciones", count(summary.get("totalTransactions")));
        addRow(table, "Ticket promedio", money(summary.get("averageTicket")));
        document.add(table);
    }

    private void addYearOverYearSection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        Map<String, Object> yoy = reportService.yearOverYearComparison(from, to, actor);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) yoy.get("current");
        @SuppressWarnings("unchecked")
        Map<String, Object> previous = (Map<String, Object>) yoy.get("previous");
        BigDecimal changePercent = (BigDecimal) yoy.get("changePercent");

        document.add(sectionTitle("Comparativo contra el año anterior"));
        document.add(paragraph("Mismo rango un año atrás: " + DAY_FMT.format((LocalDate) yoy.get("previousFrom")) +
                " al " + DAY_FMT.format((LocalDate) yoy.get("previousTo")), FONT_BODY, Element.ALIGN_LEFT, 0, 6));

        PdfPTable table = new PdfPTable(new float[]{2, 1, 1});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(6);
        addHeaderCell(table, "", Element.ALIGN_LEFT);
        addHeaderCell(table, "Este periodo", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Año anterior", Element.ALIGN_RIGHT);
        table.addCell(cell("Total vendido", Element.ALIGN_LEFT, FONT_BODY));
        table.addCell(cell(money(current.get("totalSales")), Element.ALIGN_RIGHT, FONT_BODY));
        table.addCell(cell(money(previous.get("totalSales")), Element.ALIGN_RIGHT, FONT_BODY));
        table.addCell(cell("Transacciones", Element.ALIGN_LEFT, FONT_BODY));
        table.addCell(cell(count(current.get("totalTransactions")), Element.ALIGN_RIGHT, FONT_BODY));
        table.addCell(cell(count(previous.get("totalTransactions")), Element.ALIGN_RIGHT, FONT_BODY));
        document.add(table);

        String changeText = changePercent == null
                ? "Sin ventas en el mismo rango del año anterior — no se puede calcular un % de cambio."
                : "Cambio: " + (changePercent.signum() >= 0 ? "+" : "") + changePercent.setScale(1, RoundingMode.HALF_UP) + "% respecto al año anterior.";
        document.add(paragraph(changeText, FONT_BODY_BOLD, Element.ALIGN_LEFT, 0, 14));
    }

    private void addSalesByDaySection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.salesByDay(from, to, actor);
        document.add(sectionTitle("Ventas por día"));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{2, 2, 1});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Fecha", Element.ALIGN_LEFT);
        addHeaderCell(table, "Total vendido", Element.ALIGN_RIGHT);
        addHeaderCell(table, "# Ventas", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(dateLabel(row[0], DAY_FMT), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(money(row[1]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(count(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    private void addSalesByMonthSection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.salesByMonth(from, to, actor);
        document.add(sectionTitle("Ventas por mes (para detectar temporadas)"));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{2, 2, 1});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Mes", Element.ALIGN_LEFT);
        addHeaderCell(table, "Total vendido", Element.ALIGN_RIGHT);
        addHeaderCell(table, "# Ventas", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(capitalize(dateLabel(row[0], MONTH_FMT)), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(money(row[1]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(count(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    private void addPaymentMethodSection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.salesByPaymentMethod(from, to, actor);
        document.add(sectionTitle("Ventas por método de pago"));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{2, 2, 1});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Método", Element.ALIGN_LEFT);
        addHeaderCell(table, "Total vendido", Element.ALIGN_RIGHT);
        addHeaderCell(table, "# Ventas", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(metodoPagoEs((PaymentMethod) row[0]), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(money(row[1]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(count(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    private void addTopSellersSection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.topSellers(from, to, 10, actor);
        document.add(sectionTitle("Ranking de vendedores"));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{3, 2, 1});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Vendedor", Element.ALIGN_LEFT);
        addHeaderCell(table, "Total vendido", Element.ALIGN_RIGHT);
        addHeaderCell(table, "# Ventas", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(String.valueOf(row[1]), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(money(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(count(row[3]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    private void addTopProductsSection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.topProducts(from, to, 10, actor);
        document.add(sectionTitle("Productos más vendidos"));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{3, 1, 2});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Producto", Element.ALIGN_LEFT);
        addHeaderCell(table, "Cantidad", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Ingreso", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(String.valueOf(row[1]), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(quantity(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(money(row[3]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    private void addMarginSection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.topProductsByMargin(from, to, 10, actor);
        document.add(sectionTitle("Rentabilidad por producto"));
        document.add(paragraph("Usa el costo ACTUAL de cada producto, no el que tenía al momento de venderse.", FONT_SUBTITLE, Element.ALIGN_LEFT, 0, 6));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{3, 2, 2, 2});
        table.setWidthPercentage(85);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Producto", Element.ALIGN_LEFT);
        addHeaderCell(table, "Ingreso", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Costo estimado", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Margen", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(String.valueOf(row[1]), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(money(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(money(row[3]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(money(row[4]), Element.ALIGN_RIGHT, FONT_BODY_BOLD));
        }
        document.add(table);
    }

    private void addCategorySection(Document document, LocalDate from, LocalDate to, User actor) throws DocumentException {
        List<Object[]> rows = reportService.salesByCategory(from, to, actor);
        document.add(sectionTitle("Ventas por categoría"));
        if (rows.isEmpty()) { document.add(noData()); return; }
        PdfPTable table = new PdfPTable(new float[]{3, 1, 2});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingAfter(14);
        addHeaderCell(table, "Categoría", Element.ALIGN_LEFT);
        addHeaderCell(table, "Cantidad", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Total vendido", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(String.valueOf(row[1]), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(quantity(row[2]), Element.ALIGN_RIGHT, FONT_BODY));
            table.addCell(cell(money(row[3]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    private void addLowStockSection(Document document, User actor) throws DocumentException {
        Map<String, Object> inventory = reportService.inventoryStatus(actor);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) inventory.get("lowStockProducts");
        document.add(sectionTitle("Productos con stock bajo (estado actual)"));
        if (rows == null || rows.isEmpty()) {
            document.add(paragraph("Ningún producto está por debajo de su stock mínimo ahora mismo.", FONT_BODY, Element.ALIGN_LEFT, 0, 0));
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{3, 1, 1});
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        addHeaderCell(table, "Producto", Element.ALIGN_LEFT);
        addHeaderCell(table, "Stock actual", Element.ALIGN_RIGHT);
        addHeaderCell(table, "Stock mínimo", Element.ALIGN_RIGHT);
        for (Object[] row : rows) {
            table.addCell(cell(String.valueOf(row[1]), Element.ALIGN_LEFT, FONT_BODY));
            table.addCell(cell(count(row[2]), Element.ALIGN_RIGHT, FONT_BODY_BOLD));
            table.addCell(cell(count(row[3]), Element.ALIGN_RIGHT, FONT_BODY));
        }
        document.add(table);
    }

    // ── Helpers de armado del documento ─────────────────────────────────────

    private Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, FONT_SECTION);
        p.setSpacingBefore(6);
        p.setSpacingAfter(8);
        return p;
    }

    private Paragraph noData() {
        return paragraph("Sin datos para este rango de fechas.", FONT_BODY, Element.ALIGN_LEFT, 0, 14);
    }

    private Paragraph paragraph(String text, Font font, int align, float spacingBefore, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(align);
        p.setSpacingBefore(spacingBefore);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY_BOLD));
        cell.setBackgroundColor(GRAY_FILL);
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private PdfPCell cell(String text, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        return cell;
    }

    private void addRow(PdfPTable table, String label, String value) {
        table.addCell(cell(label, Element.ALIGN_LEFT, FONT_BODY));
        table.addCell(cell(value, Element.ALIGN_RIGHT, FONT_BODY_BOLD));
    }

    // ── Conversión de los valores crudos que devuelven los queries agregados ──

    private String money(Object o) {
        if (o == null) return MONEY_FMT.format(BigDecimal.ZERO);
        BigDecimal bd = o instanceof BigDecimal ? (BigDecimal) o : new BigDecimal(o.toString());
        return MONEY_FMT.format(bd);
    }

    private String count(Object o) {
        return o == null ? "0" : String.valueOf(((Number) o).longValue());
    }

    /** Cantidades de producto (soportan decimales, ej. 1.5 kg), sin ceros de más al final. */
    private String quantity(Object o) {
        if (o == null) return "0";
        BigDecimal bd = o instanceof BigDecimal ? (BigDecimal) o : new BigDecimal(o.toString());
        return bd.stripTrailingZeros().toPlainString();
    }

    private String metodoPagoEs(PaymentMethod pm) {
        if (pm == null) return "—";
        return switch (pm) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
        };
    }

    private String dateLabel(Object o, DateTimeFormatter fmt) {
        return fmt.format(toLocalDateTime(o));
    }

    /**
     * Normaliza a {@link LocalDateTime} el valor de fecha que venga de un query agregado —
     * puede llegar como {@link Timestamp} (queries nativos, ej. {@code date_trunc}) o como
     * {@link LocalDate}/{@link java.sql.Date} (queries JPQL con {@code CAST(... AS date)}),
     * según cómo lo traduzca Hibernate.
     */
    private LocalDateTime toLocalDateTime(Object o) {
        if (o instanceof Timestamp ts) return ts.toLocalDateTime();
        if (o instanceof LocalDateTime ldt) return ldt;
        if (o instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        if (o instanceof LocalDate ld) return ld.atStartOfDay();
        throw new IllegalStateException("Tipo de fecha inesperado en el reporte: " + o.getClass());
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
