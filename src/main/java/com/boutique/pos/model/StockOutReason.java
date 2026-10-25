package com.boutique.pos.model;

/**
 * Motivo obligatorio al quitar piezas manualmente ("Quitar piezas" en Inventario). Cada uno
 * se guarda con su {@link MovementSource} y una etiqueta legible como motivo del
 * movimiento, para poder sumar la merma por tipo en vez de depender de texto libre.
 */
public enum StockOutReason {
    MERMA_DANADO(MovementSource.MERMA, "Merma – dañado"),
    MERMA_CADUCADO(MovementSource.MERMA, "Merma – caducado"),
    MERMA_ROBO(MovementSource.MERMA, "Merma – robo/extravío"),
    COUNT_DIFFERENCE(MovementSource.COUNT_DIFFERENCE, "Diferencia de conteo"),
    RETURN_TO_SUPPLIER(MovementSource.RETURN_TO_SUPPLIER, "Devolución a proveedor"),
    INTERNAL_USE(MovementSource.INTERNAL_USE, "Uso interno"),
    OTHER(MovementSource.MANUAL_OUT, "Otro");

    private final MovementSource source;
    private final String label;

    StockOutReason(MovementSource source, String label) {
        this.source = source;
        this.label = label;
    }

    public MovementSource getSource() {
        return source;
    }

    public String getLabel() {
        return label;
    }
}
