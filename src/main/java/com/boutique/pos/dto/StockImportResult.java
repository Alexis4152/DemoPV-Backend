package com.boutique.pos.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Resultado de "Actualizar existencias" ({@code POST /api/products/stock-import}): cuántas
 * filas con "Cantidad real" se procesaron y qué pasó con cada una.
 */
@Data
@Builder
public class StockImportResult {
    /** Filas con "Cantidad real" capturada (las vacías no cuentan: no cambian nada). */
    private int totalRows;
    /** Productos cuyo stock cambió (se registró un movimiento de inventario). */
    private int updated;
    /** Productos donde la cantidad real ya era igual al stock del sistema. */
    private int unchanged;
    private int errorCount;
    /** Primeros errores, con el número de fila tal como se ve en Excel. */
    private List<BulkImportRowError> errors;
}
