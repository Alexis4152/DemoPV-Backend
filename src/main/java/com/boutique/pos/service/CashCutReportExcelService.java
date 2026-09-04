package com.boutique.pos.service;

import com.boutique.pos.model.*;
import com.boutique.pos.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Arma el Excel (3 hojas: Resumen / Ventas / Productos) que se manda al admin de una tienda
// cada vez que se cierra uno o más cortes (a mano o por el job automático).
/**
 * Genera el reporte Excel (.xlsx, vía Apache POI) del cierre de caja diario de una
 * tienda, consumido por {@link CashCutReportNotifier} para adjuntarlo al correo que
 * reciben los administradores.
 *
 * <p>El libro tiene tres hojas:</p>
 * <ul>
 *   <li><b>Resumen</b>: una fila por cada corte cerrado (cajero, apertura, cierre,
 *   fondo inicial, ventas, gastos, fondo final) más totales y utilidad del periodo.</li>
 *   <li><b>Ventas</b>: totales por método de pago y el detalle de cada venta completada
 *   (se excluyen las canceladas), agrupadas por cajero, incluyendo el descuento total de
 *   cada venta.</li>
 *   <li><b>Productos</b>: el detalle línea por línea de cada producto vendido, con
 *   categoría, piezas, descuento aplicado a esa línea y subtotal.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CashCutReportExcelService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SaleRepository saleRepository;

    /**
     * Construye el libro de Excel completo (Resumen, Ventas, Productos) para el
     * conjunto de cortes cerrados de una tienda.
     *
     * <p>Las ventas de cada corte se agrupan por cajero (el {@link User} dueño del
     * corte), incluyendo solo las ventas en estado {@code COMPLETED} — las canceladas no
     * entran a los totales ni al detalle.</p>
     *
     * @param tienda tienda a la que pertenece el reporte
     * @param cuts cortes cerrados a incluir en el reporte (de un mismo día, típicamente)
     * @return el archivo .xlsx generado, en bytes
     * @throws RuntimeException si Apache POI falla al construir o escribir el libro
     */
    public byte[] build(Tienda tienda, List<CashCut> cuts) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle bold = boldStyle(wb);
            CellStyle money = moneyStyle(wb);

            buildResumen(wb.createSheet("Resumen"), header, bold, money, tienda, cuts);

            // las ventas y sus productos, agrupadas por cajero (una lista por cada corte que le pertenece)
            Map<User, List<Sale>> salesByCajero = new LinkedHashMap<>();
            for (CashCut cut : cuts) {
                List<Sale> sales = saleRepository.findByCashCutId(cut.getId()).stream()
                        .filter(s -> s.getStatus() == SaleStatus.COMPLETED)
                        .toList();
                salesByCajero.computeIfAbsent(cut.getUser(), k -> new ArrayList<>()).addAll(sales);
            }

            buildVentas(wb.createSheet("Ventas"), header, bold, money, salesByCajero);
            buildProductos(wb.createSheet("Productos"), header, bold, money, tienda, salesByCajero);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el reporte de cierre en Excel", e);
        }
    }

    /**
     * Construye la hoja "Resumen": una fila por corte con sus montos clave, más la fila
     * de totales y la de utilidad (ventas menos gastos) al final.
     *
     * @param sheet hoja de Excel a llenar
     * @param header estilo de encabezado
     * @param bold estilo de texto en negritas (etiquetas y totales)
     * @param money estilo de formato de moneda
     * @param tienda tienda del reporte, para el título
     * @param cuts cortes cerrados a listar
     */
    private void buildResumen(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                               Tienda tienda, List<CashCut> cuts) {
        int r = 0;
        row(sheet, r++, bold, "TIENDA", tienda.getName());
        r++;
        header(sheet, r++, header, "CAJERO", "APERTURA", "CIERRE", "FONDO INICIAL", "TOTAL VENTAS", "GASTOS", "FONDO FINAL");

        BigDecimal totalVentas = BigDecimal.ZERO, totalGastos = BigDecimal.ZERO, totalFondoInicial = BigDecimal.ZERO;
        for (CashCut cut : cuts) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(cut.getUser().getName());
            row.createCell(1).setCellValue(cut.getOpenedAt() != null ? cut.getOpenedAt().format(FMT) : "—");
            row.createCell(2).setCellValue(cut.getClosedAt() != null ? cut.getClosedAt().format(FMT) : "—");
            moneyCell(row, 3, money, cut.getOpeningAmount());
            moneyCell(row, 4, money, cut.getTotalSales());
            moneyCell(row, 5, money, cut.getExpenses());
            moneyCell(row, 6, money, cut.getClosingAmount());
            totalVentas = totalVentas.add(nz(cut.getTotalSales()));
            totalGastos = totalGastos.add(nz(cut.getExpenses()));
            totalFondoInicial = totalFondoInicial.add(nz(cut.getOpeningAmount()));
        }

        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 3, money, totalFondoInicial);
        moneyCell(totalRow, 4, money, totalVentas);
        moneyCell(totalRow, 5, money, totalGastos);
        moneyCell(totalRow, 6, money, totalFondoInicial.add(totalVentas).subtract(totalGastos));

        r++;
        Row utilidadRow = sheet.createRow(r);
        utilidadRow.createCell(0).setCellValue("UTILIDAD");
        cellStyle(utilidadRow, 0, bold);
        moneyCell(utilidadRow, 1, money, totalVentas.subtract(totalGastos));

        autosize(sheet, 7);
    }

    /**
     * Construye la hoja "Ventas": primero un mini-resumen de totales por método de pago
     * (efectivo/tarjeta/transferencia) y luego el detalle fila por fila de cada venta
     * completada, agrupadas por cajero.
     *
     * @param sheet hoja de Excel a llenar
     * @param header estilo de encabezado
     * @param bold estilo de texto en negritas
     * @param money estilo de formato de moneda
     * @param salesByCajero ventas completadas de todos los cortes, agrupadas por cajero
     */
    private void buildVentas(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                              Map<User, List<Sale>> salesByCajero) {
        int r = 0;

        Map<PaymentMethod, long[]> counts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal> totals = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod pm : PaymentMethod.values()) {
            counts.put(pm, new long[]{0});
            totals.put(pm, BigDecimal.ZERO);
        }
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (List<Sale> sales : salesByCajero.values()) {
            for (Sale s : sales) {
                counts.get(s.getPaymentMethod())[0]++;
                totals.put(s.getPaymentMethod(), totals.get(s.getPaymentMethod()).add(s.getTotal()));
                grandTotal = grandTotal.add(s.getTotal());
            }
        }
        r += 2;
        for (PaymentMethod pm : PaymentMethod.values()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(spanishMethod(pm));
            row.createCell(1).setCellValue(counts.get(pm)[0]);
            moneyCell(row, 2, money, totals.get(pm));
        }
        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 2, money, grandTotal);
        r += 2;

        header(sheet, r++, header, "ID_VENTA", "FECHA", "CLIENTE", "METODO", "VENDEDOR", "DESCUENTO", "TOTAL");
        for (Map.Entry<User, List<Sale>> entry : salesByCajero.entrySet()) {
            for (Sale s : entry.getValue()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(s.getId());
                row.createCell(1).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "—");
                row.createCell(2).setCellValue(s.getCustomerName() != null && !s.getCustomerName().isBlank() ? s.getCustomerName() : "—");
                row.createCell(3).setCellValue(spanishMethod(s.getPaymentMethod()));
                row.createCell(4).setCellValue(entry.getKey().getName());
                moneyCell(row, 5, money, s.getDiscount());
                moneyCell(row, 6, money, s.getTotal());
            }
        }
        autosize(sheet, 7);
    }

    /**
     * Construye la hoja "Productos": una fila por cada línea de producto vendida (no por
     * venta), con su categoría, cantidad y subtotal, más el gran total al final.
     *
     * @param sheet hoja de Excel a llenar
     * @param header estilo de encabezado
     * @param bold estilo de texto en negritas
     * @param money estilo de formato de moneda
     * @param tienda tienda del reporte, repetida en cada fila
     * @param salesByCajero ventas completadas de todos los cortes, agrupadas por cajero
     */
    private void buildProductos(Sheet sheet, CellStyle header, CellStyle bold, CellStyle money,
                                 Tienda tienda, Map<User, List<Sale>> salesByCajero) {
        int r = 0;
        header(sheet, r++, header, "ID_VENTA", "FECHA", "PRODUCTO", "CATEGORIA", "PIEZAS VENDIDAS", "VENDEDOR", "TIENDA", "DESCUENTO", "SUBTOTAL");

        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;
        for (Map.Entry<User, List<Sale>> entry : salesByCajero.entrySet()) {
            for (Sale s : entry.getValue()) {
                for (SaleItem item : s.getItems()) {
                    Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(s.getId());
                    row.createCell(1).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : "—");
                    row.createCell(2).setCellValue(item.getProductName());
                    row.createCell(3).setCellValue(item.getProduct() != null && item.getProduct().getCategory() != null
                            ? item.getProduct().getCategory().getName() : "—");
                    row.createCell(4).setCellValue(item.getQuantity().doubleValue());
                    row.createCell(5).setCellValue(entry.getKey().getName());
                    row.createCell(6).setCellValue(tienda.getName());
                    moneyCell(row, 7, money, item.getDiscount());
                    moneyCell(row, 8, money, item.getSubtotal());
                    grandTotal = grandTotal.add(item.getSubtotal());
                    grandDiscount = grandDiscount.add(nz(item.getDiscount()));
                }
            }
        }
        Row totalRow = sheet.createRow(r);
        totalRow.createCell(0).setCellValue("TOTAL");
        cellStyle(totalRow, 0, bold);
        moneyCell(totalRow, 7, money, grandDiscount);
        moneyCell(totalRow, 8, money, grandTotal);

        autosize(sheet, 9);
    }

    /**
     * Traduce el método de pago al español (en mayúsculas) para las hojas del reporte.
     *
     * @param pm método de pago
     * @return texto en español
     */
    private String spanishMethod(PaymentMethod pm) {
        return switch (pm) {
            case CASH -> "EFECTIVO";
            case CARD -> "TARJETA";
            case TRANSFER -> "TRANSFERENCIAS";
        };
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private void row(Sheet sheet, int r, CellStyle style, String label, String value) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(label);
        cellStyle(row, 0, style);
        row.createCell(1).setCellValue(value);
    }

    private void header(Sheet sheet, int r, CellStyle style, String... labels) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
    }

    private void moneyCell(Row row, int col, CellStyle style, BigDecimal value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private void cellStyle(Row row, int col, CellStyle style) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellStyle(style);
    }

    private void autosize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
    }

    private CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle boldStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        return s;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return s;
    }
}
