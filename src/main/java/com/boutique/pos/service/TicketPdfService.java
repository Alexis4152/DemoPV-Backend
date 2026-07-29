package com.boutique.pos.service;

import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class TicketPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC);
    private static final Font TOTAL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);

    public byte[] generate(Sale sale) {
        Document document = new Document(PageSize.A5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(paragraph("Boutique POS", TITLE_FONT, Element.ALIGN_CENTER));
            document.add(paragraph("Ticket de venta #" + sale.getId(), HEADER_FONT, Element.ALIGN_CENTER));
            document.add(Chunk.NEWLINE);

            document.add(paragraph("Fecha: " + (sale.getCreatedAt() != null ? sale.getCreatedAt().format(DATE_FMT) : "—"), NORMAL_FONT, Element.ALIGN_LEFT));
            document.add(paragraph("Atendió: " + (sale.getUser() != null ? sale.getUser().getName() : "—"), NORMAL_FONT, Element.ALIGN_LEFT));
            if (sale.getCustomerName() != null && !sale.getCustomerName().isBlank()) {
                document.add(paragraph("Cliente: " + sale.getCustomerName(), NORMAL_FONT, Element.ALIGN_LEFT));
            }
            document.add(paragraph("Método de pago: " + sale.getPaymentMethod(), NORMAL_FONT, Element.ALIGN_LEFT));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(new float[]{4, 1, 2, 2});
            table.setWidthPercentage(100);
            addHeaderCell(table, "Producto");
            addHeaderCell(table, "Cant.");
            addHeaderCell(table, "P. Unit.");
            addHeaderCell(table, "Subtotal");

            for (SaleItem item : sale.getItems()) {
                table.addCell(cell(item.getProductName(), Element.ALIGN_LEFT));
                table.addCell(cell(item.getQuantity().toPlainString(), Element.ALIGN_CENTER));
                table.addCell(cell(money(item.getUnitPrice()), Element.ALIGN_RIGHT));
                table.addCell(cell(money(item.getSubtotal()), Element.ALIGN_RIGHT));
            }
            document.add(table);
            document.add(Chunk.NEWLINE);

            document.add(paragraph("Subtotal: " + money(sale.getSubtotal()), NORMAL_FONT, Element.ALIGN_RIGHT));
            if (sale.getDiscount() != null && sale.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
                document.add(paragraph("Descuento: -" + money(sale.getDiscount()), NORMAL_FONT, Element.ALIGN_RIGHT));
            }
            if (sale.getTax() != null && sale.getTax().compareTo(BigDecimal.ZERO) > 0) {
                document.add(paragraph("Impuestos: " + money(sale.getTax()), NORMAL_FONT, Element.ALIGN_RIGHT));
            }
            document.add(paragraph("Total: " + money(sale.getTotal()), TOTAL_FONT, Element.ALIGN_RIGHT));
            document.add(Chunk.NEWLINE);
            document.add(paragraph("Gracias por su compra", SMALL_FONT, Element.ALIGN_CENTER));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el ticket en PDF", e);
        }
        return out.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(new Color(240, 240, 240));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private PdfPCell cell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, NORMAL_FONT));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        return cell;
    }

    private Paragraph paragraph(String text, Font font, int align) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(align);
        return p;
    }

    private String money(BigDecimal amount) {
        return "$" + (amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
    }
}
