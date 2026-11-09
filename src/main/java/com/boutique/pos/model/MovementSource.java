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
    /** Devolución de una venta de un turno ya cerrado: el cliente entregó la pieza y regresa al stock. */
    SALE_REFUND,
    /**
     * Devolución de una venta en la que el cliente NO entregó la pieza: el stock no cambia,
     * pero queda el registro para que se pueda revisar (ver SaleService#refund).
     */
    SALE_REFUND_NO_PRODUCT,
    /**
     * Al apartar: confirmar o registrar el anticipo (las piezas salen). Lo sobre pedido y por
     * pedido deja un renglón informativo (ajuste, sin cambiar el stock).
     */
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
    RAW_MATERIAL,
    /**
     * Entrega de un apartado pagado completo (ver ApartadoService#deliver): informativo, el stock
     * no cambia (ya se descontó en el anticipo, o es sobre pedido y no se descontó).
     */
    APARTADO_DELIVERED,
    /** Abono a un apartado con anticipo: informativo, un renglón por producto; el stock no cambia. */
    APARTADO_INSTALLMENT
}
