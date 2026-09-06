package com.boutique.pos.model;

/**
 * Estado de un {@link Apartado} a lo largo de su ciclo de vida.
 *
 * <pre>
 *   PENDING  --confirmar--&gt; ACTIVE --completar--&gt; COMPLETED
 *      |                      |
 *   cancelar              cancelar / vence
 *      v                      v
 *  CANCELLED              CANCELLED / EXPIRED
 * </pre>
 */
public enum ApartadoStatus {
    /** Recién creado desde la tienda pública; el stock TODAVÍA no se ha descontado —
     *  solo es una solicitud esperando que un cajero/admin la revise. */
    PENDING,
    /** Confirmado por un cajero/admin: el stock ya se descontó y corre el conteo hacia
     *  {@link Apartado#getExpiresAt()}. */
    ACTIVE,
    /** El cliente recogió y pagó el producto: se generó una {@link Sale} real
     *  ({@link Apartado#getSaleId()}) sin volver a descontar stock. */
    COMPLETED,
    /** Cancelado por el cliente o por un cajero/admin antes de completarse. Si ya estaba
     *  {@code ACTIVE}, el stock se restituye al cancelar. */
    CANCELLED,
    /** Se cumplió {@link Apartado#getExpiresAt()} sin que se completara ni cancelara —
     *  el job programado ({@code ApartadoExpiryJob}) lo marca así y restituye el stock. */
    EXPIRED
}
