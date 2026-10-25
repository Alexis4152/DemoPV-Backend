package com.boutique.pos.model;

/**
 * Acciones de Inventario que NO mueven stock (esas quedan en {@link InventoryMovement}) pero
 * que se registran en la bitácora ({@link InventoryAuditLog}) para la pestaña "Movimientos".
 */
public enum InventoryAuditAction {
    PRODUCT_CREATED,
    /** Edición de datos del producto; el detalle dice qué cambió (ej. "Precio: 100 → 120"). */
    PRODUCT_UPDATED,
    /** Producto desactivado (baja del catálogo). */
    PRODUCT_DEACTIVATED,
    /** Carga masiva de productos por Excel; el detalle trae el resumen. */
    BULK_IMPORT,
    /** Archivo de "Actualizar existencias"; cada cambio de stock queda además como movimiento. */
    STOCK_IMPORT_FILE,
    BARCODES_GENERATED,
    LABELS_PRINTED,
    /** Descarga de información del inventario (Excel/PDF, hoja de códigos, plantilla). */
    EXPORT,
    /**
     * Ya no se genera (se quitó la reactivación de productos). Se conserva solo para que los
     * registros que ya existen sigan siendo válidos en la restricción de la columna (ver
     * InventoryEnumConstraintMigration) y se sigan mostrando en "Movimientos".
     */
    PRODUCT_REACTIVATED
}
