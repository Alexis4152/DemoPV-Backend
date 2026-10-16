package com.boutique.pos.model;

/**
 * Tipo de movimiento de materia prima registrado en {@link RawMaterialMovement}.
 */
public enum RawMaterialMovementType {
    /** Entrada de piezas (compra, stock inicial). */
    IN,
    /** Salida de piezas que no van al inventario (merma, uso interno, corrección). */
    OUT,
    /** Salida de piezas que pasaron al inventario vendible (entran a un {@link Product}). */
    TO_INVENTORY
}
