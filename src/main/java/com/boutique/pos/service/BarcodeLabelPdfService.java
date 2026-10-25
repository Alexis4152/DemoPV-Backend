package com.boutique.pos.service;

import com.boutique.pos.model.Product;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PDF de etiquetas de código de barras: una etiqueta por página, y cada página mide
 * exactamente lo que la etiqueta (ej. 50×25 mm). Así sirve igual para imprimir desde la
 * PC (driver de la impresora térmica) que desde el celular (app de la impresora, eligiendo
 * el mismo tamaño). Contenido: solo las barras (Code 128) y el código abajo.
 */
@Service
public class BarcodeLabelPdfService {

    /** Tamaños de etiqueta soportados, en mm (ancho × alto). */
    public static final Map<String, float[]> SIZES = Map.of(
            "50x25", new float[]{50, 25},
            "51x25", new float[]{51, 25},
            "50x30", new float[]{50, 30},
            "40x30", new float[]{40, 30},
            "60x40", new float[]{60, 40});

    public static final String DEFAULT_SIZE = "50x25";

    private static final float PT_PER_MM = 72f / 25.4f;
    // Módulos de margen en blanco a cada lado de las barras ("quiet zone"): sin ellos
    // la pistola no distingue dónde empieza el código.
    private static final int QUIET_ZONE_MODULES = 10;

    /**
     * Genera el PDF de etiquetas.
     *
     * @param products productos a imprimir (todos deben tener código), en el orden deseado
     * @param copies   etiquetas por producto
     * @param size     clave de {@link #SIZES}; si no es válida se usa {@link #DEFAULT_SIZE}
     * @return el PDF en bytes
     */
    public byte[] build(List<Product> products, int copies, String size) {
        float[] mm = SIZES.getOrDefault(size, SIZES.get(DEFAULT_SIZE));
        float width = mm[0] * PT_PER_MM;
        float height = mm[1] * PT_PER_MM;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(new Rectangle(width, height), 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfContentByte cb = writer.getDirectContent();
            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);

            boolean first = true;
            for (Product p : products) {
                for (int i = 0; i < copies; i++) {
                    if (!first) document.newPage();
                    first = false;
                    document.add(barcodeImage(cb, font, p.getBarcode(), width, height));
                }
            }
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF de etiquetas", e);
        }
        return out.toByteArray();
    }

    private static final int SHEET_COLUMNS = 3;
    // Caracteres de descripción que caben en ~2 renglones de un recuadro de la hoja.
    private static final int SHEET_DESCRIPTION_MAX = 80;
    private static final String UNCATEGORIZED = "Sin categoría";
    // Ancho máximo del módulo (barra más delgada) en la hoja: ~0.42 mm, cómodo para la
    // pistola desde papel impreso en cualquier impresora normal.
    private static final float SHEET_MAX_MODULE = 1.2f;

    /**
     * Hoja tamaño carta con el nombre y el código de barras de cada producto, en rejilla de
     * {@value #SHEET_COLUMNS} columnas — para tenerla impresa en caja y escanear desde ella.
     *
     * @param products productos con código, ya en el orden deseado (alfabético)
     * @param title    encabezado de la hoja (ej. nombre de la tienda)
     * @return el PDF en bytes
     */
    public byte[] buildSheet(List<Product> products, String title) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER, 28, 28, 28, 28);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfContentByte cb = writer.getDirectContent();
            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            float cellWidth = (document.right() - document.left()) / SHEET_COLUMNS;

            // Una sección por categoría, cada una desde una hoja nueva; dentro, los productos
            // conservan el orden en que llegan (alfabético).
            boolean first = true;
            for (Map.Entry<String, List<Product>> section : groupByCategory(products).entrySet()) {
                if (!first) document.newPage();
                first = false;
                document.add(categoryTable(cb, font, cellWidth, title, section.getKey(), section.getValue()));
            }
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar la hoja de códigos", e);
        }
        return out.toByteArray();
    }

    /** Productos agrupados por nombre de categoría (alfabético); los sin categoría al final. */
    private static Map<String, List<Product>> groupByCategory(List<Product> products) {
        Map<String, List<Product>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Product> uncategorized = new ArrayList<>();
        for (Product p : products) {
            String category = p.getCategory() != null ? p.getCategory().getName() : null;
            if (category == null || category.isBlank()) uncategorized.add(p);
            else groups.computeIfAbsent(category, k -> new ArrayList<>()).add(p);
        }
        Map<String, List<Product>> ordered = new LinkedHashMap<>(groups);
        if (!uncategorized.isEmpty()) ordered.put(UNCATEGORIZED, uncategorized);
        return ordered;
    }

    /**
     * Rejilla de una categoría. El encabezado (título de la hoja + nombre de la categoría)
     * es la fila de encabezado de la tabla, así que se repite si la categoría ocupa varias hojas.
     */
    private PdfPTable categoryTable(PdfContentByte cb, BaseFont font, float cellWidth, String title,
                                    String category, List<Product> products) throws Exception {
        PdfPTable table = new PdfPTable(SHEET_COLUMNS);
        table.setWidthPercentage(100);

        PdfPCell header = new PdfPCell();
        header.setColspan(SHEET_COLUMNS);
        header.setBorder(Rectangle.NO_BORDER);
        header.setPaddingBottom(8);
        Paragraph sheetTitle = new Paragraph(title, new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(110, 110, 110)));
        header.addElement(sheetTitle);
        Paragraph categoryTitle = new Paragraph(category + "  (" + products.size() + ")", new Font(Font.HELVETICA, 14, Font.BOLD));
        header.addElement(categoryTitle);
        table.addCell(header);
        table.setHeaderRows(1);

        Font nameFont = new Font(Font.HELVETICA, 8, Font.BOLD);
        Font descFont = new Font(Font.HELVETICA, 7, Font.NORMAL, new Color(110, 110, 110));
        for (Product p : products) {
            PdfPCell cell = new PdfPCell();
            cell.setBorderColor(new Color(220, 220, 220));
            cell.setPadding(6);
            // Mínima, no fija: con altura fija lo que no cabe (el código, debajo de un
            // nombre o descripción de 2 líneas) se omite sin avisar.
            cell.setMinimumHeight(80);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph name = new Paragraph(p.getName(), nameFont);
            name.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(name);

            String description = shortDescription(p.getDescription());
            if (description != null) {
                Paragraph desc = new Paragraph(description, descFont);
                desc.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(desc);
            }

            Image img = sheetBarcode(cb, font, p.getBarcode(), cellWidth - 16);
            img.setAlignment(Image.ALIGN_CENTER);
            // Sin esto la celda estira la imagen a todo su ancho: barras enormes que se
            // enciman con el nombre.
            img.setWidthPercentage(0);
            img.setSpacingBefore(4);
            cell.addElement(img);
            table.addCell(cell);
        }
        // Completa la última fila para que la rejilla no quede cortada.
        int remainder = products.size() % SHEET_COLUMNS;
        if (remainder != 0) {
            for (int i = remainder; i < SHEET_COLUMNS; i++) {
                PdfPCell empty = new PdfPCell();
                empty.setBorder(Rectangle.NO_BORDER);
                table.addCell(empty);
            }
        }
        return table;
    }

    /** Descripción en una sola línea y recortada, para que no empuje el código fuera del recuadro. */
    private static String shortDescription(String description) {
        if (description == null || description.isBlank()) return null;
        String d = description.trim().replaceAll("\\s+", " ");
        return d.length() > SHEET_DESCRIPTION_MAX ? d.substring(0, SHEET_DESCRIPTION_MAX - 1).trim() + "…" : d;
    }

    /** Código de barras para la hoja: ancho de módulo fijo (con tope) y altura moderada. */
    private Image sheetBarcode(PdfContentByte cb, BaseFont font, String code, float maxWidth) throws Exception {
        Barcode128 barcode = new Barcode128();
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setCode(code);
        barcode.setFont(font);
        barcode.setSize(8);
        barcode.setBaseline(8);
        barcode.setX(1f);
        float modules = barcode.getBarcodeSize().getWidth();
        barcode.setX(Math.min(SHEET_MAX_MODULE, maxWidth / (modules + 2 * QUIET_ZONE_MODULES)));
        barcode.setBarHeight(28);
        return barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
    }

    /** Barras + texto abajo, escalado al ancho de la etiqueta y centrado en la página. */
    private Image barcodeImage(PdfContentByte cb, BaseFont font, String code, float width, float height) throws Exception {
        float margin = 2 * PT_PER_MM;
        float fontSize = Math.min(10f, height * 0.14f);

        Barcode128 barcode = new Barcode128();
        barcode.setCodeType(Barcode128.CODE128);
        barcode.setCode(code);
        barcode.setFont(font);
        barcode.setSize(fontSize);
        barcode.setBaseline(fontSize);

        // Ancho de módulo para que barras + quiet zone ocupen el ancho de la etiqueta.
        barcode.setX(1f);
        float modules = barcode.getBarcodeSize().getWidth();
        barcode.setX((width - 2 * margin) / (modules + 2 * QUIET_ZONE_MODULES));
        barcode.setBarHeight(height - 2 * margin - fontSize * 1.6f);

        Image img = barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
        img.setAbsolutePosition((width - img.getScaledWidth()) / 2, (height - img.getScaledHeight()) / 2);
        return img;
    }
}
