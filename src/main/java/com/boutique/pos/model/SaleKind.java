package com.boutique.pos.model;

/**
 * Qué representa una {@link Sale} dentro del ciclo de un anticipo — ver {@link Apartado#getOrigin()}
 * y {@code ApartadoService#createWithDeposit}/{@code #complete}.
 */
public enum SaleKind {
    /** Venta normal de mostrador, sin relación con ningún apartado. */
    REGULAR,
    /** El anticipo que el cliente dejó al encargar un producto — se registra como venta
     *  en el momento en que se recibe, para que cuadre en el corte de caja de quien lo
     *  cobró. Ver {@link Sale#getApartadoId()}. */
    DEPOSIT,
    /** El saldo restante que el cliente paga al recoger un apartado con anticipo
     *  ({@code total - depositAmount}). Ver {@link Sale#getApartadoId()}. */
    BALANCE
}
