package com.boutique.pos.service;

import com.boutique.pos.dto.BulkImportRowError;
import com.boutique.pos.dto.StockImportResult;
import com.boutique.pos.exception.FieldConflictException;
import com.boutique.pos.model.InventoryAuditAction;
import com.boutique.pos.model.Product;
import com.boutique.pos.model.User;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Actualizar existencias": lee la hoja de conteo de inventario en Excel (la misma que
 * genera {@link InventoryCountSheetService}) ya llenada, y deja el stock de cada producto
 * en su "Cantidad real" — lo que se contó físicamente. Pensado para la carga inicial de un
 * cliente nuevo (dio de alta sus productos sin piezas) y para los conteos periódicos.
 *
 * <p>Cada producto se busca por su código (no por nombre) dentro de la tienda del actor.
 * Una "Cantidad real" vacía significa "no cambiar". La diferencia contra el stock actual
 * se aplica con {@link ProductService#setStockTo} (mismas reglas que un ajuste manual): queda
 * registrado un movimiento de inventario, y requiere el permiso "Ajustar stock". Cada
 * fila es independiente: una fila con error no detiene a las demás.</p>
 */
@Service
@RequiredArgsConstructor
public class StockImportService {

    static final String REASON = "Carga de existencias (Excel)";
    private static final int MAX_INLINE_ERRORS = 50;
    // Filas donde se busca el encabezado (la hoja trae título y subtítulo arriba).
    private static final int HEADER_SEARCH_ROWS = 15;
    private static final int GENERATED_CODE_LENGTH = 8;

    private final ProductService productService;
    private final TenantScope tenantScope;
    private final InventoryAuditService auditService;

    /**
     * @param file  Excel (.xlsx) con al menos las columnas "Código" y "Cantidad real"
     * @param actor usuario que sube el archivo; determina la tienda y si puede bajar stock
     * @return resumen: actualizados, sin cambio y errores por fila
     * @throws IllegalArgumentException si el archivo no se puede leer o no trae esas columnas
     * @throws IllegalStateException si el actor no tiene una tienda elegida
     */
    public StockImportResult importFile(MultipartFile file, User actor) {
        if (tenantScope.tiendaForWrite(actor) == null) {
            throw new IllegalStateException("Elige una tienda para poder actualizar existencias");
        }

        int totalRows = 0;
        int updated = 0;
        int unchanged = 0;
        List<BulkImportRowError> errors = new ArrayList<>();
        Set<Long> seenProducts = new HashSet<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int[] header = findHeader(sheet, formatter);
            int headerRow = header[0];
            int codeCol = header[1];
            int realCol = header[2];

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String realText = value(row, realCol, formatter);
                if (realText.isBlank()) continue; // sin cantidad real: no se cambia
                totalRows++;
                int excelRow = r + 1;

                try {
                    int real = parseQuantity(realText);
                    String code = value(row, codeCol, formatter);
                    if (code.isBlank()) {
                        throw new IllegalArgumentException("La fila no tiene código. Genera los códigos y vuelve a descargar la hoja.");
                    }
                    Product p = findByCode(code, actor);
                    if (!seenProducts.add(p.getId())) {
                        throw new IllegalArgumentException("El código " + code + " está repetido en el archivo");
                    }
                    if (productService.setStockTo(p.getId(), real, REASON, actor) == 0) unchanged++;
                    else updated++;
                } catch (FieldConflictException | IllegalArgumentException | IllegalStateException ex) {
                    errors.add(new BulkImportRowError(excelRow, ex.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo — ¿es un Excel (.xlsx) válido?");
        }

        auditService.log(actor, InventoryAuditAction.STOCK_IMPORT_FILE,
                ProductBulkImportService.fileLabel(file) + ": " + updated + " actualizado(s), " + unchanged + " sin cambio, "
                        + errors.size() + " con error");
        return StockImportResult.builder()
                .totalRows(totalRows)
                .updated(updated)
                .unchanged(unchanged)
                .errorCount(errors.size())
                .errors(errors.size() > MAX_INLINE_ERRORS ? errors.subList(0, MAX_INLINE_ERRORS) : errors)
                .build();
    }

    /**
     * Ubica la fila de encabezados y las columnas "Código" y "Cantidad real" (por nombre,
     * sin importar acentos/mayúsculas ni el orden de las columnas).
     *
     * @return {fila del encabezado, columna del código, columna de cantidad real}
     */
    private int[] findHeader(Sheet sheet, DataFormatter formatter) {
        String codeKey = normalize(InventoryCountSheetService.CODE_HEADER);
        String realKey = normalize(InventoryCountSheetService.REAL_HEADER);
        int last = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int codeCol = -1;
            int realCol = -1;
            for (Cell cell : row) {
                String h = normalize(formatter.formatCellValue(cell));
                if (h.equals(codeKey)) codeCol = cell.getColumnIndex();
                if (h.equals(realKey)) realCol = cell.getColumnIndex();
            }
            if (codeCol >= 0 && realCol >= 0) return new int[]{r, codeCol, realCol};
        }
        throw new IllegalArgumentException("El archivo no tiene las columnas \"Código\" y \"Cantidad real\". "
                + "Descarga la hoja de inventario en Excel y llena la columna \"Cantidad real\".");
    }

    /**
     * Busca el producto por código en la tienda del actor. Si Excel convirtió un código
     * generado a número y le quitó los ceros (00700001 → 700001), se reintenta con los
     * ceros de vuelta.
     */
    private Product findByCode(String code, User actor) {
        Product p = productService.findByBarcode(code, actor);
        if (p == null && code.matches("\\d{1," + (GENERATED_CODE_LENGTH - 1) + "}")) {
            p = productService.findByBarcode("0".repeat(GENERATED_CODE_LENGTH - code.length()) + code, actor);
        }
        if (p == null) {
            throw new IllegalArgumentException("No existe un producto activo con el código " + code + " en esta tienda");
        }
        return p;
    }

    /** Cantidad entera ≥ 0; acepta "12" o "12.0" (como lo puede guardar Excel). */
    private int parseQuantity(String text) {
        String t = text.trim().replace(",", "");
        try {
            double d = Double.parseDouble(t);
            if (d != Math.floor(d)) throw new IllegalArgumentException("La cantidad real debe ser un número entero: " + text);
            if (d < 0) throw new IllegalArgumentException("La cantidad real no puede ser negativa: " + text);
            if (d > Integer.MAX_VALUE) throw new IllegalArgumentException("La cantidad real es demasiado grande: " + text);
            return (int) d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La cantidad real no es un número: " + text);
        }
    }

    private String value(Row row, int col, DataFormatter formatter) {
        Cell cell = row.getCell(col);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static String normalize(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.trim().toLowerCase(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }
}
