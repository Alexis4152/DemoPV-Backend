package com.boutique.pos.model;

/**
 * De dónde vino un {@link Apartado} — separa, en la pantalla de Apartados, los que el
 * cliente mismo solicitó desde la tienda pública de los que un cajero registró en
 * persona al recibir un anticipo.
 */
public enum ApartadoOrigin {
    /** Solicitado por el cliente desde la tienda pública ({@code /apartar/{slug}}), sin
     *  cuenta ni login — el flujo de siempre. */
    ONLINE,
    /** Registrado por un cajero/admin en el Punto de Venta al recibir un anticipo en
     *  persona — nace ya {@code ACTIVE} (sin pasar por {@code PENDING}, el cajero lo
     *  confirma ahí mismo) y trae {@link Apartado#getDepositAmount()}. */
    DEPOSIT
}
