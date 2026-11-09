package com.boutique.pos.service;

import com.boutique.pos.model.Apartado;
import com.boutique.pos.model.ApartadoItem;
import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleKind;
import com.boutique.pos.model.SaleStatus;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.repository.ApartadoRepository;
import com.boutique.pos.repository.ProductRepository;
import com.boutique.pos.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Collator;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Genera el Excel del reporte diario de inventario de una tienda (ver {@link InventoryReportService}):
 * los productos activos con sus piezas actuales, y los anticipos con productos sobre pedido que
 * siguen por entregar.
 */
@Service
@RequiredArgsConstructor
public class InventoryReportExcelService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale MX = Locale.forLanguageTag("es-MX");

    private final ProductRepository productRepository;
    private final ApartadoRepository apartadoRepository;
    private final SaleRepository saleRepository;

    /**
     * @param tienda tienda del reporte
     * @return el archivo .xlsx generado, en bytes
     */
    public byte[] build(Tienda tienda) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("$#,##0.00"));
            money.setVerticalAlignment(VerticalAlignment.TOP);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            buildInventario(wb.createSheet("Inventario"), header, tienda);
            buildAnticipos(wb.createSheet("Anticipos sobre pedido"), header, money, wrap, tienda);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el reporte de inventario en Excel", e);
        }
    }

    /** Hoja "Inventario": producto con su descripción, categoría y piezas, en orden alfabético. */
    private void buildInventario(Sheet sheet, CellStyle header, Tienda tienda) {
        Collator collator = Collator.getInstance(MX);
        collator.setStrength(Collator.PRIMARY);
        List<Product> products = new ArrayList<>(productRepository.findAllActive(tienda.getId()));
        products.sort(Comparator.comparing(this::productLabel, collator));

        int r = 0;
        header(sheet, r++, header, "PRODUCTO", "CATEGORIA", "PIEZAS");
        for (Product p : products) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(productLabel(p));
            row.createCell(1).setCellValue(p.getCategory() != null ? p.getCategory().getName() : "—");
            row.createCell(2).setCellValue(p.getStock() != null ? p.getStock() : 0);
        }
        sheet.createFreezePane(0, 1);
        for (int c = 0; c < 3; c++) sheet.autoSizeColumn(c);
    }

    private String productLabel(Product p) {
        String description = p.getDescription() != null ? p.getDescription().trim() : "";
        return description.isEmpty() ? p.getName() : p.getName() + " " + description;
    }

    /** Hoja "Anticipos sobre pedido": un renglón por anticipo, por fecha de entrega. */
    private void buildAnticipos(Sheet sheet, CellStyle header, CellStyle money, CellStyle wrap, Tienda tienda) {
        int r = 0;
        header(sheet, r++, header, "FOLIO_APARTADO", "CLIENTE", "TELEFONO", "FECHA REGISTRO", "FECHA ENTREGA", "PRODUCTOS SOBRE PEDIDO",
                "TOTAL", "PAGADO", "SALDO", "ESTADO", "DETALLE DE PAGOS");
        for (Apartado a : apartadoRepository.findOnOrderDeposits(tienda.getId())) {
            List<Long> finalSale = a.getSaleId() != null ? List.of(a.getSaleId()) : List.of(-1L);
            List<Sale> payments = saleRepository.findApartadoPayments(a.getId(), finalSale).stream()
                    .filter(s -> s.getStatus() == SaleStatus.COMPLETED).toList();
            BigDecimal paid = payments.stream().map(this::netPaid).reduce(BigDecimal.ZERO, BigDecimal::add);

            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(a.getFolio());
            text(row, 1, wrap, a.getCustomerName());
            text(row, 2, wrap, a.getCustomerPhone());
            text(row, 3, wrap, a.getConfirmedAt() != null ? a.getConfirmedAt().format(FMT) : "—");
            text(row, 4, wrap, a.getDeliveryDate() != null ? a.getDeliveryDate().format(DAY) : "—");
            text(row, 5, wrap, a.getItems().stream().filter(i -> isOnOrder(i) && i.getDeliveredAt() == null)
                    .map(i -> i.getQuantity().stripTrailingZeros().toPlainString() + " x " + i.getProductName())
                    .collect(Collectors.joining("\n")));
            money(row, 6, money, a.getTotal());
            money(row, 7, money, paid);
            money(row, 8, money, a.getTotal().subtract(paid));
            text(row, 9, wrap, !Boolean.TRUE.equals(a.getPendingDelivery()) ? "Activo"
                    : a.getItems().stream().anyMatch(i -> i.getDeliveredAt() != null) ? "Pagado, entregado en parte" : "Pagado, por entregar");
            text(row, 10, wrap, payments.stream().map(this::paymentLine).collect(Collectors.joining("\n")));
        }
        sheet.createFreezePane(0, 1);
        for (int c = 0; c < 11; c++) sheet.autoSizeColumn(c);
    }

    private boolean isOnOrder(ApartadoItem i) {
        return Boolean.TRUE.equals(i.getOnOrder())
                || (i.getProduct() != null && Boolean.TRUE.equals(i.getProduct().getIsMadeToOrder()));
    }

    /** Lo que sigue pagado de un cobro: si se devolvió, lo que no se le regresó al cliente. */
    private BigDecimal netPaid(Sale s) {
        if (s.getRefundedAt() == null) return s.getTotal();
        return s.getRefundAmount() != null ? s.getTotal().subtract(s.getRefundAmount()) : BigDecimal.ZERO;
    }

    private String paymentLine(Sale s) {
        String kind = s.getKind() != SaleKind.DEPOSIT ? "Liquidación"
                : Boolean.TRUE.equals(s.getInstallment()) ? "Abono" : "Anticipo";
        NumberFormat currency = NumberFormat.getCurrencyInstance(MX);
        return kind + " " + currency.format(s.getTotal()) + " · " + method(s.getPaymentMethod())
                + " · " + s.getCreatedAt().format(FMT) + " · venta " + s.getFolio();
    }

    private String method(PaymentMethod pm) {
        return switch (pm) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
        };
    }

    private void text(Row row, int col, CellStyle style, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "—");
        cell.setCellStyle(style);
    }

    private void money(Row row, int col, CellStyle style, BigDecimal value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }

    private void header(Sheet sheet, int r, CellStyle style, String... labels) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < labels.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(labels[i]);
            cell.setCellStyle(style);
        }
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
}
