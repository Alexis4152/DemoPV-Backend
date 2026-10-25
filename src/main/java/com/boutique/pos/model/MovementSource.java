package com.boutique.pos.model;

/**
 * De dónde viene un {@link InventoryMovement} — más fino que {@link MovementType} (que solo
 * dice si entró o salió): permite filtrar en la pestaña "Movimientos" de Inventario y medir
 * la merma por separado de, por ejemplo, una devolución a proveedor.
 *
 * <p>Los movimientos anteriores a este campo se clasifican a partir de su motivo al
 * arrancar (ver {@code MovementSourceBackfill}).</p>
 */
public enum MovementSource {
    /** Venta en el punto de venta. */
    SALE,
    /** Cancelación de una venta (las piezas regresan). */
    SALE_CANCEL,
    /** Apartado confirmado (las piezas salen). */
    APARTADO,
    /** Apartado cancelado o vencido (las piezas regresan). */
    APARTADO_RETURN,
    /** Stock con el que se dio de alta el producto. */
    INITIAL_STOCK,
    /** "Agregar piezas" manual. */
    MANUAL_IN,
    /** Merma: dañado, caducado, robo o extravío. */
    MERMA,
    /** Diferencia encontrada al contar (ajuste manual). */
    COUNT_DIFFERENCE,
    RETURN_TO_SUPPLIER,
    INTERNAL_USE,
    /** "Quitar piezas" manual con otro motivo. */
    MANUAL_OUT,
    /** "Actualizar existencias (Excel)": stock fijado a la cantidad real contada. */
    STOCK_IMPORT,
    /** Piezas pasadas desde materia prima. */
    RAW_MATERIAL
}
