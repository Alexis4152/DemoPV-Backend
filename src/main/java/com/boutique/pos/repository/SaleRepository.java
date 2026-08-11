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
    @Query("SELECT s FROM Sale s WHERE " +
           "(:tiendaId IS NULL OR s.tienda.id = :tiendaId) " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND (:customerName IS NULL OR LOWER(s.customerName) LIKE LOWER(CONCAT('%', CAST(:customerName AS string), '%'))) " +
           "AND (:paymentMethod IS NULL OR s.paymentMethod = :paymentMethod) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "ORDER BY s.createdAt DESC")
    Page<Sale> search(@Param("tiendaId") Long tiendaId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("customerName") String customerName,
                       @Param("paymentMethod") PaymentMethod paymentMethod,
                       @Param("status") SaleStatus status,
                       Pageable pageable);

    /** Lista las ventas asociadas a un corte de caja (usado al armar el detalle/reporte de un corte). */
    List<Sale> findByCashCutId(Long cashCutId);

    /**
     * Suma el total de las ventas completadas dentro de un rango de fechas.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por
     * tienda). {@code from}/{@code to} siempre llegan con un valor real (nunca {@code null}),
     * por la limitación de Postgres para inferir el tipo de un parámetro timestamp nulo.
     */
    @Query("SELECT COALESCE(SUM(s.total),0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId)")
    BigDecimal totalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

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
    @Query("SELECT CAST(s.createdAt AS date), COALESCE(SUM(s.total),0), COUNT(s) " +
           "FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId) " +
           "GROUP BY CAST(s.createdAt AS date) ORDER BY CAST(s.createdAt AS date)")
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
}
