package com.boutique.pos.repository;

import com.boutique.pos.model.PaymentMethod;
import com.boutique.pos.model.Sale;
import com.boutique.pos.model.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de {@link Sale}, la venta principal (cabecera de ticket).
 */
public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Un solo query con todos los filtros opcionales (fecha, cliente, método, estado) —
    // reemplaza los dos métodos que había antes (con/sin rango de fechas).
    // from/to siempre vienen con un valor real (nunca null) — ver SaleService.findAll,
    // porque Postgres no logra inferir el tipo de un parámetro timestamp nulo ni con CAST.
    /**
     * Búsqueda paginada de ventas con filtros combinables.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por
     * tienda); cualquier otro valor restringe el resultado a esa tienda. {@code customerName}
     * hace coincidencia parcial insensible a mayúsculas (nulo = sin filtrar); {@code paymentMethod}
     * y {@code status} son opcionales (nulo = cualquier valor). {@code from}/{@code to} siempre
     * llegan con un valor real desde el service (nunca {@code null}), por la limitación de
     * Postgres para inferir el tipo de un parámetro timestamp nulo (ver comentario arriba).
     */
    // Sin ORDER BY: el orden llega en el Pageable (ver SortParams / SaleController#list).
    @Query("SELECT s FROM Sale s LEFT JOIN s.user su WHERE " +
           "(:tiendaId IS NULL OR s.tienda.id = :tiendaId) " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND (:customerName IS NULL OR LOWER(s.customerName) LIKE LOWER(CONCAT('%', CAST(:customerName AS string), '%'))) " +
           "AND (:paymentMethod IS NULL OR s.paymentMethod = :paymentMethod) " +
           "AND (:status IS NULL OR s.status = :status) " +
           // refunded: null = cualquiera; true = solo devueltas; false = solo NO devueltas
           // (así "Completada" y "Devuelta" no se mezclan en el filtro de Ventas).
           "AND (:refunded IS NULL OR (:refunded = true AND s.refundedAt IS NOT NULL) " +
           "     OR (:refunded = false AND s.refundedAt IS NULL))")
    Page<Sale> search(@Param("tiendaId") Long tiendaId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("customerName") String customerName,
                       @Param("paymentMethod") PaymentMethod paymentMethod,
                       @Param("status") SaleStatus status,
                       @Param("refunded") Boolean refunded,
                       Pageable pageable);

    /** Ventas canceladas de un corte (para mostrarlas en el turno, ver CashCutService#cancelledSales). */
    List<Sale> findByCashCutIdAndStatusOrderByCancelledAtAsc(Long cashCutId, SaleStatus status);

    /** Lista las ventas asociadas a un corte de caja (usado al armar el detalle/reporte de un corte). */
    List<Sale> findByCashCutId(Long cashCutId);

    /** Ventas devueltas en efectivo desde alguno de estos cortes (de ese u otro turno). */
    List<Sale> findByRefundCashCutIdIn(List<Long> cutIds);

    /** Ventas de una caja devueltas con tarjeta o transferencia desde {@code since} (no pasan por un corte). */
    @Query("SELECT s FROM Sale s WHERE s.cashCut.cashRegister.id = :registerId AND s.refundedAt > :since " +
           "AND s.paymentMethod <> com.boutique.pos.model.PaymentMethod.CASH")
    List<Sale> findNonCashRefundedSince(@Param("registerId") Long registerId, @Param("since") LocalDateTime since);

    String NON_CASH_REFUNDS_IN_TURN =
            "WHERE s.cashCut.cashRegister.id = :registerId AND s.refundedAt <= :to " +
            "AND s.refundedAt > COALESCE((SELECT MAX(c.closedAt) FROM CashCut c " +
            "    WHERE c.cashRegister.id = :registerId AND c.closedAt <= :openedAt), :openedAt) " +
            "AND s.status = com.boutique.pos.model.SaleStatus.COMPLETED " +
            "AND s.paymentMethod <> com.boutique.pos.model.PaymentMethod.CASH";

    /**
     * Lo devuelto con tarjeta o transferencia en un turno de una caja, por método de pago, sea
     * cual sea el turno de la venta. No pasa por un corte, así que se ubica por la hora: desde
     * que cerró el turno anterior de la caja (lo devuelto entre turnos cae en el siguiente,
     * como en el Excel) hasta {@code to}.
     */
    @Query("SELECT s.paymentMethod, COALESCE(SUM(COALESCE(s.refundAmount, s.total)), 0) FROM Sale s " +
           NON_CASH_REFUNDS_IN_TURN + " GROUP BY s.paymentMethod")
    List<Object[]> sumNonCashRefundsInTurn(@Param("registerId") Long registerId,
                                           @Param("openedAt") LocalDateTime openedAt,
                                           @Param("to") LocalDateTime to);

    /** Las mismas devoluciones, una por una (para la tabla de movimientos del turno). */
    @Query("SELECT s FROM Sale s " + NON_CASH_REFUNDS_IN_TURN + " ORDER BY s.refundedAt ASC")
    List<Sale> findNonCashRefundsInTurn(@Param("registerId") Long registerId,
                                        @Param("openedAt") LocalDateTime openedAt,
                                        @Param("to") LocalDateTime to);

    /** Lo devuelto con tarjeta o transferencia de las ventas de un corte sin caja, por método de pago. */
    @Query("SELECT s.paymentMethod, COALESCE(SUM(COALESCE(s.refundAmount, s.total)), 0) FROM Sale s " +
           "WHERE s.cashCut.id = :cutId AND s.refundedAt IS NOT NULL " +
           "AND s.status = com.boutique.pos.model.SaleStatus.COMPLETED " +
           "AND s.paymentMethod <> com.boutique.pos.model.PaymentMethod.CASH GROUP BY s.paymentMethod")
    List<Object[]> sumNonCashRefundsByCashCut(@Param("cutId") Long cutId);

    /** Cobros de un apartado (anticipo, saldo o pago completo); las ventas finales de antes
     *  de ligarlas por apartado_id se encuentran por su id ({@code ids}). */
    @Query("SELECT s FROM Sale s WHERE s.apartadoId = :apartadoId OR s.id IN :ids ORDER BY s.createdAt ASC")
    List<Sale> findApartadoPayments(@Param("apartadoId") Long apartadoId, @Param("ids") java.util.Collection<Long> ids);

    /** Los mismos cobros ya como filas del desglose, sin cargar cada venta con su corte y su tienda. */
    @Query("SELECT new com.boutique.pos.dto.ApartadoPaymentRow(s.id, s.folio, s.kind, s.createdAt, s.paymentMethod, " +
           "s.total, s.status, s.cancelledAt, s.refundedAt, u.name, s.installment, s.refundAmount) " +
           "FROM Sale s LEFT JOIN s.user u WHERE s.apartadoId = :apartadoId OR s.id IN :ids ORDER BY s.createdAt ASC")
    List<com.boutique.pos.dto.ApartadoPaymentRow> findApartadoPaymentRows(@Param("apartadoId") Long apartadoId,
                                                                          @Param("ids") java.util.Collection<Long> ids);

    /**
     * Totales de las ventas de un corte agrupados por método de pago y estado:
     * filas {@code [PaymentMethod, SaleStatus, cantidad, suma de total]}. Lo usa el cálculo
     * del efectivo esperado ({@code CashBalanceCalculator}), que corre en casi todo el flujo
     * de cortes: una sola consulta agregada en vez de cargar cada venta con sus productos.
     */
    @Query("SELECT s.paymentMethod, s.status, COUNT(s), COALESCE(SUM(s.total), 0) FROM Sale s " +
           "WHERE s.cashCut.id = :cutId GROUP BY s.paymentMethod, s.status")
    List<Object[]> sumByCashCut(@Param("cutId") Long cutId);

    /**
     * Suma el total de las ventas completadas dentro de un rango de fechas.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por
     * tienda). {@code from}/{@code to} siempre llegan con un valor real (nunca {@code null}),
     * por la limitación de Postgres para inferir el tipo de un parámetro timestamp nulo.
     */
    // Neto: ventas del periodo MENOS las devoluciones hechas en el periodo (por la fecha de la
    // devolución, no de la venta — igual que el corte de caja, ver SaleService#refund).
    @Query("SELECT COALESCE(SUM(s.total),0) - (SELECT COALESCE(SUM(COALESCE(r.refundAmount, r.total)),0) FROM Sale r " +
           "  WHERE r.refundedAt BETWEEN :from AND :to AND (:tiendaId IS NULL OR r.tienda.id = :tiendaId)) " +
           "FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId)")
    BigDecimal totalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /** Total devuelto en el periodo (por la fecha de la devolución), sin importar cuándo fue la venta. */
    @Query("SELECT COALESCE(SUM(COALESCE(r.refundAmount, r.total)),0) FROM Sale r WHERE r.refundedAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR r.tienda.id = :tiendaId)")
    BigDecimal refundTotalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /** Cuántas ventas se devolvieron en el periodo (por la fecha de la devolución). */
    @Query("SELECT COUNT(r) FROM Sale r WHERE r.refundedAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR r.tienda.id = :tiendaId)")
    long refundCountBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /**
     * Cuenta las ventas completadas dentro de un rango de fechas.
     *
     * <p>Mismo patrón de filtros que {@link #totalBetween}: {@code tiendaId} nulo = todas las
     * tiendas; {@code from}/{@code to} siempre llegan con un valor real (nunca {@code null}).
     */
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId)")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /**
     * Agrupa las ventas completadas por día dentro de un rango de fechas, devolviendo
     * {@code [fecha, totalVendidoEseDia, numeroDeVentasEseDia]} por cada día con ventas.
     * Usado para el reporte de ventas por día. Mismo patrón de filtros que {@link #totalBetween}.
     */
    // Neto por día: cada devolución resta en el día en que se hizo (el número de ventas no
    // cambia: la venta sí ocurrió). Nativa por el UNION ALL.
    @Query(value = "SELECT d, COALESCE(SUM(amount),0), SUM(cnt) FROM (" +
                   "  SELECT CAST(s.created_at AS date) AS d, s.total AS amount, 1 AS cnt FROM sales s " +
                   "  WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "  AND (CAST(:tiendaId AS bigint) IS NULL OR s.tienda_id = :tiendaId) " +
                   "  UNION ALL " +
                   "  SELECT CAST(s.refunded_at AS date), -COALESCE(s.refund_amount, s.total), 0 FROM sales s " +
                   "  WHERE s.refunded_at BETWEEN :from AND :to " +
                   "  AND (CAST(:tiendaId AS bigint) IS NULL OR s.tienda_id = :tiendaId)" +
                   ") x GROUP BY d ORDER BY d",
           nativeQuery = true)
    List<Object[]> salesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /**
     * Lista los productos activos con stock igual o por debajo del mínimo, como
     * {@code [id, nombre, stock, stockMinimo]}, para el reporte/alerta de bajo inventario.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por tienda).
     */
    @Query("SELECT p.id, p.name, p.stock, p.minStock FROM Product p WHERE p.isActive = true AND p.stock <= p.minStock " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId) ORDER BY p.stock ASC")
    List<Object[]> lowStockProducts(@Param("tiendaId") Long tiendaId);

    /**
     * Agrupa las ventas completadas por método de pago dentro de un rango de fechas,
     * devolviendo {@code [metodoDePago, totalVendido, numeroDeVentas]} por cada método con
     * al menos una venta. Útil para cuadrar caja (cuánto entró en efectivo vs. tarjeta vs.
     * transferencia). Mismo patrón de filtros que {@link #totalBetween}.
     */
    // Neto por método: la devolución resta en el método con que se pagó (el reembolso va por
    // el mismo medio). Nativa por el UNION ALL: el método llega como texto ("CASH"...).
    @Query(value = "SELECT m, COALESCE(SUM(amount),0), SUM(cnt) FROM (" +
                   "  SELECT s.payment_method AS m, s.total AS amount, 1 AS cnt FROM sales s " +
                   "  WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "  AND (CAST(:tiendaId AS bigint) IS NULL OR s.tienda_id = :tiendaId) " +
                   "  UNION ALL " +
                   "  SELECT s.payment_method, -COALESCE(s.refund_amount, s.total), 0 FROM sales s " +
                   "  WHERE s.refunded_at BETWEEN :from AND :to " +
                   "  AND (CAST(:tiendaId AS bigint) IS NULL OR s.tienda_id = :tiendaId)" +
                   ") x GROUP BY m ORDER BY SUM(amount) DESC",
           nativeQuery = true)
    List<Object[]> salesByPaymentMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /**
     * Ranking de vendedores/cajeros por monto total vendido dentro de un rango de fechas,
     * devolviendo {@code [userId, nombreDelVendedor, totalVendido, numeroDeVentas]} ordenado
     * de mayor a menor monto. {@code pageable} limita cuántos vendedores se devuelven (ej.
     * {@code PageRequest.of(0, 5)} para el top 5); usar {@link Pageable#unpaged()} para
     * traer a todos. Mismo patrón de filtros que {@link #totalBetween}.
     */
    // Neto por vendedor: la devolución le resta a quien hizo la venta, en el periodo en que
    // se devolvió. Nativa por el UNION ALL; el Pageable solo limita cuántos se devuelven.
    @Query(value = "SELECT x.uid, u.name, COALESCE(SUM(x.amount),0), SUM(x.cnt) FROM (" +
                   "  SELECT s.user_id AS uid, s.total AS amount, 1 AS cnt FROM sales s " +
                   "  WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "  AND (CAST(:tiendaId AS bigint) IS NULL OR s.tienda_id = :tiendaId) " +
                   "  UNION ALL " +
                   "  SELECT s.user_id, -COALESCE(s.refund_amount, s.total), 0 FROM sales s " +
                   "  WHERE s.refunded_at BETWEEN :from AND :to " +
                   "  AND (CAST(:tiendaId AS bigint) IS NULL OR s.tienda_id = :tiendaId)" +
                   ") x JOIN users u ON u.id = x.uid GROUP BY x.uid, u.name ORDER BY SUM(x.amount) DESC",
           nativeQuery = true)
    List<Object[]> topSellers(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("tiendaId") Long tiendaId, Pageable pageable);

    /**
     * Agrupa el total vendido por mes dentro de un rango de fechas, devolviendo
     * {@code [primerDiaDelMes, totalVendidoEseMes, numeroDeVentas]} por cada mes con
     * ventas — pensado para detectar temporadas altas/bajas cuando el rango seleccionado
     * cubre varios meses (el desglose por día del reporte de "Ventas por día" se vuelve
     * difícil de leer en rangos largos). Mismo patrón de filtros que {@link #totalBetween}.
     */
    // Neto por mes: igual que salesByDay, la devolución resta en el mes en que se hizo.
    @Query(value = "SELECT mo, COALESCE(SUM(amount),0), SUM(cnt) FROM (" +
                   "  SELECT date_trunc('month', s.created_at) AS mo, s.total AS amount, 1 AS cnt FROM sales s " +
                   "  WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "  AND (:tiendaId IS NULL OR s.tienda_id = :tiendaId) " +
                   "  UNION ALL " +
                   "  SELECT date_trunc('month', s.refunded_at), -COALESCE(s.refund_amount, s.total), 0 FROM sales s " +
                   "  WHERE s.refunded_at BETWEEN :from AND :to " +
                   "  AND (:tiendaId IS NULL OR s.tienda_id = :tiendaId)" +
                   ") x GROUP BY mo ORDER BY mo",
           nativeQuery = true)
    List<Object[]> salesByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    /** Usado únicamente por {@code TiendaPurgeService} (borrado físico y total de una tienda); sus {@code SaleItem} caen con ella (ON DELETE CASCADE). */
    void deleteByTiendaId(Long tiendaId);

    /**
     * Ventas cuyo cliente coincide con el correo dado (en cualquier tienda) — usado por
     * {@code ArcoRequestService} para armar el reporte de datos de una solicitud de Acceso.
     * {@link Sale} no guarda teléfono del cliente (a diferencia de {@link
     * com.boutique.pos.model.Apartado}), solo nombre y correo.
     */
    List<Sale> findByCustomerEmailIgnoreCase(String customerEmail);
}
