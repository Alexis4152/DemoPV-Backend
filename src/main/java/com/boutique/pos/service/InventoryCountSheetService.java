package com.boutique.pos.service;

import com.boutique.pos.model.Product;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.security.TenantScope;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera la hoja de conteo físico de inventario (Excel o PDF): una fila por producto
 * activo de la tienda con su código, nombre, descripción, categoría, la cantidad que dice el sistema y una columna
 * "Cantidad real" en blanco, para imprimirse y llenarse a mano (o capturarse en el Excel)
 * al contar la mercancía. El Excel ya llenado se puede subir en "Actualizar existencias"
 * (ver {@link StockImportService}).
 *
 * <p>Solo lee — no toca el stock. Usa las mismas librerías que el resto de reportes:
 * Apache POI para el .xlsx ({@link CashCutReportExcelService}) y OpenPDF para el PDF
 * ({@link ReportPdfService}).</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryCountSheetService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    // El Excel también es la plantilla de "Actualizar existencias" (StockImportService): ahí
    // se buscan las columnas por estos encabezados, así que no cambiarlos sin revisar ese lado.
    static final String CODE_HEADER = "Código";
    static final String REAL_HEADER = "Cantidad real";
    private static final String[] HEADERS = {CODE_HEADER, "Producto", "Descripción", "Categoría", "Cantidad actual", REAL_HEADER};

    private static final Color GRAY_FILL = new Color(238, 238, 238);
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(90, 90, 90));
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

    private final ProductService productService;
    private final TenantScope tenantScope;

    /**
     * @param actor usuario que la pide; determina la tienda (ver {@link ProductService#findAll})
     * @return el .xlsx con la hoja "Inventario", en bytes
     */
    public byte[] buildExcel(User actor) {
        List<Product> products = productService.findAll(actor);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Inventario");

            CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            CellStyle header = wb.createCellStyle();
            header.cloneStyleFrom(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordered(header);

            CellStyle body = wb.createCellStyle();
            bordered(body);

            int r = 0;
            Row title = sheet.createRow(r++);
            title.createCell(0).setCellValue(tiendaName(actor));
            title.getCell(0).setCellStyle(bold);
            sheet.createRow(r++).createCell(0).setCellValue("Conteo de inventario — generado el " + LocalDateTime.now().format(FMT));
            r++;

            Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            // Texto, no número: Excel le quitaría los ceros a la izquierda a "00700001".
            CellStyle text = wb.createCellStyle();
            text.cloneStyleFrom(body);
            text.setDataFormat(wb.createDataFormat().getFormat("@"));

            for (Product p : products) {
                Row row = sheet.createRow(r++);
                Cell code = row.createCell(0);
                code.setCellValue(p.getBarcode() != null ? p.getBarcode() : "");
                code.setCellStyle(text);
                Cell name = row.createCell(1);
                name.setCellValue(p.getName());
                name.setCellStyle(body);
                Cell description = row.createCell(2);
                description.setCellValue(p.getDescription() != null ? p.getDescription() : "");
                description.setCellStyle(body);
                Cell category = row.createCell(3);
                category.setCellValue(p.getCategory() != null ? p.getCategory().getName() : "");
                category.setCellStyle(body);
                Cell stock = row.createCell(4);
                stock.setCellValue(p.getStock() != null ? p.getStock() : 0);
                stock.setCellStyle(body);
                // "Cantidad real" va vacía a propósito: es la que se llena al contar.
                row.createCell(5).setCellStyle(body);
            }

            sheet.setColumnWidth(0, 16 * 256);
            sheet.autoSizeColumn(1);
            sheet.setColumnWidth(2, 40 * 256); // la descripción puede ser larga: ancho fijo
            sheet.autoSizeColumn(3);
            sheet.setColumnWidth(4, 16 * 256);
            sheet.setColumnWidth(5, 16 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el inventario en Excel", e);
        }
    }

    /**
     * @param actor usuario que la pide; determina la tienda (ver {@link ProductService#findAll})
     * @return el PDF (A4 horizontal, para que quepan las 6 columnas) listo para imprimir, en bytes
     */
    public byte[] buildPdf(User actor) {
        List<Product> products = productService.findAll(actor);
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(paragraph(tiendaName(actor), FONT_TITLE, 2));
            document.add(paragraph("Conteo de inventario — generado el " + LocalDateTime.now().format(FMT), FONT_SUBTITLE, 4));
            document.add(paragraph("Contó: ______________________________     Fecha del conteo: ______________", FONT_BODY, 14));

            PdfPTable table = new PdfPTable(new float[]{2.2f, 4.5f, 5.5f, 2.8f, 1.8f, 1.8f});
            table.setWidthPercentage(100);
            // El encabezado se repite en cada página: la lista suele ocupar varias hojas.
            table.setHeaderRows(1);
            addHeaderCell(table, HEADERS[0], Element.ALIGN_LEFT);
            addHeaderCell(table, HEADERS[1], Element.ALIGN_LEFT);
            addHeaderCell(table, HEADERS[2], Element.ALIGN_LEFT);
            addHeaderCell(table, HEADERS[3], Element.ALIGN_LEFT);
            addHeaderCell(table, HEADERS[4], Element.ALIGN_CENTER);
            addHeaderCell(table, HEADERS[5], Element.ALIGN_CENTER);
            for (Product p : products) {
                table.addCell(cell(p.getBarcode() != null ? p.getBarcode() : "—", Element.ALIGN_LEFT));
                table.addCell(cell(p.getName(), Element.ALIGN_LEFT));
                table.addCell(cell(p.getDescription() != null ? p.getDescription() : "", Element.ALIGN_LEFT));
                table.addCell(cell(p.getCategory() != null ? p.getCategory().getName() : "", Element.ALIGN_LEFT));
                table.addCell(cell(String.valueOf(p.getStock() != null ? p.getStock() : 0), Element.ALIGN_CENTER));
                table.addCell(cell("", Element.ALIGN_CENTER));
            }
            if (products.isEmpty()) {
                PdfPCell empty = cell("Sin productos activos", Element.ALIGN_CENTER);
                empty.setColspan(HEADERS.length);
                table.addCell(empty);
            }
            document.add(table);

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el inventario en PDF", e);
        }
        return out.toByteArray();
    }

    // Misma lógica que ReportPdfService: tiendaForWrite es "la tienda que el actor
    // representa ahora mismo" (para un SUPER_ADMIN, la que eligió actuar).
    private String tiendaName(User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        return tienda != null ? tienda.getName() : "Punto de Venta";
    }

    private static void bordered(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private Paragraph paragraph(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY_BOLD));
        cell.setBackgroundColor(GRAY_FILL);
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        table.addCell(cell);
    }

    // Padding generoso: la columna "Cantidad real" se llena a mano con pluma.
    private PdfPCell cell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(7);
        cell.setPaddingBottom(7);
        cell.setPaddingLeft(6);
        cell.setPaddingRight(6);
        return cell;
    }
}
