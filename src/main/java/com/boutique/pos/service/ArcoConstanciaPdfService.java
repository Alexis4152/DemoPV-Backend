package com.boutique.pos.service;

import com.boutique.pos.model.ArcoRequest;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;

/**
 * Genera la "constancia" en PDF de una solicitud ARCO: comprobante formal de que se recibió
 * y qué se resolvió/ejecutó, para adjuntar al correo que se le manda al titular al
 * responder ({@code ArcoRequestService#respond}) y al ejecutar ({@code #execute}). Documento
 * de PLATAFORMA (no de una tienda): siempre lleva el logo de Nexora, nunca el de una tienda.
 * Usa la misma librería (OpenPDF) que {@link TicketPdfService}, con un documento mucho más
 * simple: solo párrafos de texto, sin tabla.
 */
@Service
@Slf4j
public class ArcoConstanciaPdfService {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color GRAY_TEXT = new Color(90, 90, 90);
    private static final Color GRAY_LINE = new Color(190, 190, 190);

    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONT_SUBTLE = FontFactory.getFont(FontFactory.HELVETICA, 9, GRAY_TEXT);
    private static final Font FONT_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);

    /**
     * Genera la constancia de una solicitud ARCO con su estado actual (sirve tanto para
     * "ya se respondió" como para "ya se ejecutó" — simplemente refleja lo que haya en ese
     * momento, sin dos plantillas separadas).
     *
     * @param request solicitud ya guardada, con los campos que se quieran reflejar ya
     *                establecidos (outcome/resolutionNotes, o además executedAt/executionSummary)
     * @return el PDF generado
     */
    public byte[] generate(ArcoRequest request) {
        Document document = new Document(PageSize.LETTER, 54, 54, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            addLogo(document);
            document.add(paragraph("Nexora Systems", FONT_SUBTLE, Element.ALIGN_CENTER, 0, 2));
            document.add(paragraph("Constancia de solicitud de derechos ARCO", FONT_TITLE, Element.ALIGN_CENTER, 0, 4));
            document.add(paragraph("Folio #" + request.getId(), FONT_SUBTLE, Element.ALIGN_CENTER, 0, 12));
            addSeparator(document);

            field(document, "Solicitante", request.getRequesterName() + " (" + request.getRequesterEmail() + ")");
            if (Boolean.TRUE.equals(request.getRepresentative()) && request.getRepresentativeName() != null) {
                field(document, "Representante", request.getRepresentativeName());
            }
            field(document, "Tipo de derecho ejercido", request.getType().label());
            if (request.getReceivedAt() != null) field(document, "Recibida formalmente", request.getReceivedAt().format(FECHA_FMT));
            if (request.getDueDate() != null) field(document, "Plazo legal de respuesta", request.getDueDate().toString() + (Boolean.TRUE.equals(request.getExtended()) ? " (ya extendido una vez)" : ""));
            if (request.getIdentityVerifiedAt() != null) {
                field(document, "Identidad verificada", request.getIdentityVerifiedAt().format(FECHA_FMT)
                        + (request.getIdentityMethod() != null ? " — " + request.getIdentityMethod() : ""));
            }

            if (request.getOutcome() != null) {
                addSeparator(document);
                field(document, "Resultado", request.getOutcome().name());
                if (request.getLegalBasis() != null && !request.getLegalBasis().isBlank()) {
                    field(document, "Motivo/fundamento", request.getLegalBasis());
                }
                if (request.getResolutionNotes() != null) field(document, "Respuesta", request.getResolutionNotes());
                if (request.getResolvedAt() != null) field(document, "Respondida el", request.getResolvedAt().format(FECHA_FMT));
            }

            if (request.getExecutedAt() != null) {
                addSeparator(document);
                field(document, "Ejecutada el", request.getExecutedAt().format(FECHA_FMT));
                if (request.getExecutionSummary() != null) field(document, "Qué se hizo", request.getExecutionSummary());
            }

            addSeparator(document);
            document.add(paragraph("Este documento es una constancia informativa generada automáticamente por el sistema; "
                    + "no sustituye ningún otro medio probatorio.", FONT_SUBTLE, Element.ALIGN_CENTER, 10, 0));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar la constancia ARCO en PDF", e);
        }
        return out.toByteArray();
    }

    private void field(Document document, String label, String value) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", FONT_LABEL));
        p.add(new Chunk(value, FONT_BODY));
        p.setSpacingAfter(6);
        document.add(p);
    }

    private void addLogo(Document document) {
        try (InputStream in = new ClassPathResource("branding/default-logo.png").getInputStream()) {
            Image image = Image.getInstance(in.readAllBytes());
            image.scaleToFit(60, 60);
            image.setAlignment(Image.ALIGN_CENTER);
            document.add(image);
        } catch (Exception e) {
            log.warn("No se pudo cargar el logo para la constancia ARCO: {}", e.getMessage());
        }
    }

    private void addSeparator(Document document) throws DocumentException {
        LineSeparator line = new LineSeparator(0.5f, 100, GRAY_LINE, Element.ALIGN_CENTER, -2);
        document.add(new Chunk(line));
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(8);
        document.add(spacer);
    }

    private Paragraph paragraph(String text, Font font, int alignment, float spacingBefore, float spacingAfter) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(alignment);
        p.setSpacingBefore(spacingBefore);
        p.setSpacingAfter(spacingAfter);
        return p;
    }
}
