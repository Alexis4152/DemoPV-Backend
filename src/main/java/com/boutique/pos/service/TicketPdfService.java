package com.boutique.pos.service;

import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleItem;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.TiendaInfo;
import com.boutique.pos.repository.TiendaInfoRepository;
import com.boutique.pos.util.SpanishNumberToWords;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPdfService {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final NumberFormat MONEY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    private static final Color GRAY_TEXT = new Color(90, 90, 90);
    private static final Color GRAY_LINE = new Color(190, 190, 190);
    private static final Color GRAY_FILL = new Color(238, 238, 238);

    // Jerarquía tipográfica única para todo el ticket — evita la mezcla de
    // tamaños sueltos que hacía que se viera desordenado.
    private static final Font FONT_STORE_NAME = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font FONT_SUBTLE = FontFactory.getFont(FontFactory.HELVETICA, 8, GRAY_TEXT);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font FONT_TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

    private final TiendaInfoRepository tiendaInfoRepository;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    public byte[] generate(Sale sale) {
        Tienda tienda = sale.getTienda();
        TiendaInfo info = tienda != null ? tiendaInfoRepository.findByTiendaId(tienda.getId()).orElse(null) : null;

        Document document = new Document(PageSize.A5, 36, 36, 24, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // ── Encabezado: logo, nombre de la tienda, RFC, dirección ──────────
            addLogo(document, tienda);
            document.add(paragraph(tienda != null ? tienda.getName() : "Punto de Venta Demo", FONT_STORE_NAME, Element.ALIGN_CENTER, 4, 2));
            if (info != null && info.getRfc() != null && !info.getRfc().isBlank()) {
                document.add(paragraph("RFC: " + info.getRfc(), FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));
            }
            addAddressLines(document, info);
            addSeparator(document, 10);

            // ── Datos de la venta ────────────────────────────────────────────
            String vendedor = sale.getUser() != null ? sale.getUser().getName() : "—";
            document.add(paragraph("LE ATENDIÓ: " + vendedor.toUpperCase(), FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            String fecha = sale.getCreatedAt() != null ? sale.getCreatedAt().format(FECHA_FMT) : "—";
            String hora = sale.getCreatedAt() != null ? sale.getCreatedAt().format(HORA_FMT) : "—";
            document.add(paragraph("FECHA: " + fecha + "     HORA: " + hora, FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            document.add(paragraph("FOLIO: " + sale.getId(), FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            if (sale.getCustomerName() != null && !sale.getCustomerName().isBlank()) {
                document.add(paragraph("Cliente: " + sale.getCustomerName(), FONT_BODY, Element.ALIGN_LEFT, 0, 3));
            }
            document.add(paragraph("Método de pago: " + metodoPagoEs(sale.getPaymentMethod()), FONT_BODY, Element.ALIGN_LEFT, 0, 0));
            addSeparator(document, 10);

            // ── Cuerpo: detalle de la venta ──────────────────────────────────
            PdfPTable table = new PdfPTable(new float[]{4, 1, 2, 2});
            table.setWidthPercentage(100);
            table.setSpacingAfter(10);
            addHeaderCell(table, "Producto", Element.ALIGN_LEFT);
            addHeaderCell(table, "Cant.", Element.ALIGN_CENTER);
            addHeaderCell(table, "P. Unit.", Element.ALIGN_RIGHT);
            addHeaderCell(table, "Subtotal", Element.ALIGN_RIGHT);

            BigDecimal totalArticulos = BigDecimal.ZERO;
            for (SaleItem item : sale.getItems()) {
                table.addCell(cell(item.getProductName(), Element.ALIGN_LEFT));
                table.addCell(cell(item.getQuantity().toPlainString(), Element.ALIGN_CENTER));
                table.addCell(cell(money(item.getUnitPrice()), Element.ALIGN_RIGHT));
                table.addCell(cell(money(item.getSubtotal()), Element.ALIGN_RIGHT));
                totalArticulos = totalArticulos.add(item.getQuantity());
            }
            document.add(table);

            // ── Totales ──────────────────────────────────────────────────────
            document.add(paragraph("Subtotal: " + money(sale.getSubtotal()), FONT_BODY, Element.ALIGN_RIGHT, 0, 2));
            if (sale.getDiscount() != null && sale.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
                document.add(paragraph("Descuento: -" + money(sale.getDiscount()), FONT_BODY, Element.ALIGN_RIGHT, 0, 2));
            }
            if (sale.getTax() != null && sale.getTax().compareTo(BigDecimal.ZERO) > 0) {
                document.add(paragraph("Impuestos: " + money(sale.getTax()), FONT_BODY, Element.ALIGN_RIGHT, 0, 2));
            }
            document.add(paragraph("Total: " + money(sale.getTotal()), FONT_TOTAL, Element.ALIGN_RIGHT, 4, 0));
            addSeparator(document, 10);

            // ── Pie: total con letra, artículos vendidos, marca y despedida ────
            document.add(paragraph(SpanishNumberToWords.pesos(sale.getTotal()), FONT_BODY, Element.ALIGN_CENTER, 0, 6));
            document.add(paragraph("TOTAL DE ARTÍCULOS VENDIDOS = " + stripTrailingZeros(totalArticulos), FONT_BODY_BOLD, Element.ALIGN_CENTER, 0, 10));
            addSeparator(document, 10);
            document.add(paragraph("NEXORA SYSTEMS", FONT_BODY_BOLD, Element.ALIGN_CENTER, 0, 2));
            document.add(paragraph("WWW.NEXORASYSTEMS.COM", FONT_SUBTLE, Element.ALIGN_CENTER, 0, 6));
            document.add(paragraph("¡¡¡GRACIAS POR SU COMPRA, VUELVA PRONTO!!!", FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el ticket en PDF", e);
        }
        return out.toByteArray();
    }

    private String metodoPagoEs(PaymentMethod pm) {
        if (pm == null) return "—";
        return switch (pm) {
            case CASH -> "Efectivo";
            case CARD -> "Tarjeta";
            case TRANSFER -> "Transferencia";
        };
    }

    // Calle, colonia, C.P., localidad y estado — cada uno en su propio renglón
    // (nunca todos juntos en una sola línea), y se salta el que venga vacío.
    private void addAddressLines(Document document, TiendaInfo info) throws DocumentException {
        if (info == null) return;
        addIfPresent(document, info.getCalle());
        addIfPresent(document, info.getColonia());
        addIfPresent(document, info.getCodigoPostal() != null && !info.getCodigoPostal().isBlank() ? "C.P. " + info.getCodigoPostal() : null);
        addIfPresent(document, info.getLocalidad());
        addIfPresent(document, info.getEstado());
    }

    private void addIfPresent(Document document, String text) throws DocumentException {
        if (text != null && !text.isBlank()) {
            document.add(paragraph(text, FONT_SUBTLE, Element.ALIGN_CENTER, 0, 0));
        }
    }

    // Logo de la tienda si ya subió uno; si no, el de Nexora por default.
    private void addLogo(Document document, Tienda tienda) {
        try {
            byte[] bytes = logoBytes(tienda);
            Image image = Image.getInstance(bytes);
            image.scaleToFit(70, 70);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
        } catch (Exception e) {
            log.warn("No se pudo cargar el logo para el ticket: {}", e.getMessage());
        }
    }

    private byte[] logoBytes(Tienda tienda) throws IOException {
        if (tienda != null && tienda.getLogoPath() != null && !tienda.getLogoPath().isBlank()) {
            Path path = Paths.get(uploadsDir, tienda.getLogoPath().replaceFirst("^/uploads/", ""));
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        }
        try (InputStream in = new ClassPathResource("branding/default-logo.png").getInputStream()) {
            return in.readAllBytes();
        }
    }

    private void addSeparator(Document document, float spacingAfter) throws DocumentException {
        LineSeparator line = new LineSeparator(0.5f, 100, GRAY_LINE, Element.ALIGN_CENTER, 0);
        Paragraph p = new Paragraph();
        p.add(new Chunk(line));
        p.setSpacingAfter(spacingAfter);
        document.add(p);
    }

    private void addHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY_BOLD));
        cell.setBackgroundColor(GRAY_FILL);
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private PdfPCell cell(String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        return cell;
    }

    private Paragraph paragraph(String text, Font font, int align, float spacingBefore, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(align);
        p.setSpacingBefore(spacingBefore);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    private String money(BigDecimal amount) {
        return MONEY_FMT.format(amount != null ? amount : BigDecimal.ZERO);
    }

    private String stripTrailingZeros(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }
}
