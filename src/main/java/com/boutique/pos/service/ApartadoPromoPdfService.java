package com.boutique.pos.service;

import com.boutique.pos.model.Tienda;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

/**
 * Genera el PDF promocional de una sola página que invita a los clientes de una tienda a
 * usar su vitrina pública de apartados ({@code /apartar/{slug}}) — pensado para imprimirse
 * y pegarse en el local, o compartirse por redes: nombre de la tienda, una leyenda fija,
 * y un QR (con la URL también en texto, por si no se puede escanear) que apunta a esa
 * vitrina. Usa OpenPDF, igual que {@link TicketPdfService}/{@link ReportPdfService}, y
 * ZXing para generar el QR como imagen PNG (sin depender de ningún servicio externo).
 */
@Service
@Slf4j
public class ApartadoPromoPdfService {

    private static final Color BODY_TEXT = new Color(70, 70, 70);
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26);
    private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 15, BODY_TEXT);
    private static final Font FONT_URL = FontFactory.getFont(FontFactory.HELVETICA, 11, BODY_TEXT);

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    /**
     * Genera el PDF promocional.
     *
     * @param tienda tienda para la que se genera (nombre y logo, si tiene)
     * @param publicUrl URL completa de la vitrina pública (ej. {@code https://.../apartar/mi-tienda}),
     *                  ya armada por el frontend (`origin` + slug) — el backend no conoce
     *                  su propio dominio público, así que no la reconstruye por su cuenta
     * @return el PDF generado, listo para descargarse
     * @throws IllegalStateException si OpenPDF o la generación del QR fallan
     */
    public byte[] generate(Tienda tienda, String publicUrl) {
        Document document = new Document(PageSize.LETTER, 54, 54, 90, 90);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addLogo(document, tienda);

            Paragraph name = new Paragraph(tienda.getName(), FONT_TITLE);
            name.setAlignment(Element.ALIGN_CENTER);
            name.setSpacingBefore(16);
            name.setSpacingAfter(10);
            document.add(name);

            Paragraph subtitle = new Paragraph("Consulta mi tienda en línea para realizar apartados", FONT_SUBTITLE);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(36);
            document.add(subtitle);

            Image qr = Image.getInstance(qrPngBytes(publicUrl));
            qr.scaleToFit(260, 260);
            qr.setAlignment(Image.ALIGN_CENTER);
            document.add(qr);

            Paragraph url = new Paragraph(publicUrl, FONT_URL);
            url.setAlignment(Element.ALIGN_CENTER);
            url.setSpacingBefore(20);
            document.add(url);

            document.close();
        } catch (DocumentException | WriterException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF promocional de apartados", e);
        }
        return out.toByteArray();
    }

    /** Codifica {@code content} como QR y lo devuelve ya como bytes de un PNG. */
    private byte[] qrPngBytes(String content) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 400, 400, hints);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(image, "png", pngOut);
        return pngOut.toByteArray();
    }

    // Mismo criterio que TicketPdfService#addLogo: el logo propio de la tienda si tiene
    // uno y el archivo sigue en disco, o si no, el de Nexora por default — y si ni
    // siquiera eso carga, el PDF se genera de todas formas, solo sin logo.
    private void addLogo(Document document, Tienda tienda) {
        try {
            byte[] bytes = logoBytes(tienda);
            Image image = Image.getInstance(bytes);
            image.scaleToFit(80, 80);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
        } catch (Exception e) {
            log.warn("No se pudo cargar el logo para el PDF promocional de apartados: {}", e.getMessage());
        }
    }

    private byte[] logoBytes(Tienda tienda) throws IOException {
        if (tienda.getLogoPath() != null && !tienda.getLogoPath().isBlank()) {
            Path path = Paths.get(uploadsDir, tienda.getLogoPath().replaceFirst("^/uploads/", ""));
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        }
        try (InputStream in = new ClassPathResource("branding/default-logo.png").getInputStream()) {
            return in.readAllBytes();
        }
    }
}
