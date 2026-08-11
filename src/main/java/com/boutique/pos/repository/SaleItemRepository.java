package com.boutique.pos.repository;

import com.boutique.pos.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de {@link SaleItem} (renglones de una venta), usado principalmente para reportes.
 */
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /**
     * Devuelve el top N de productos más vendidos (por cantidad) dentro de un rango de fechas,
     * considerando solo ventas completadas ({@code status = 'COMPLETED'}).
     *
     * <p>Cada fila del resultado es {@code [productId, productName, cantidadTotal, subtotalTotal]}.
     * Es un query nativo (no JPQL) porque agrupa y limita directamente sobre las tablas
     * {@code sale_items}/{@code sales}. {@code tiendaId} nulo indica SUPER_ADMIN viendo todas
     * las tiendas (no se filtra por tienda); cualquier otro valor restringe el resultado a esa
     * tienda. {@code from}/{@code to} deben llegar siempre con un valor real (nunca {@code null}),
     * ya que Postgres no puede inferir el tipo de un parámetro timestamp nulo, por lo que aquí
     * se usa {@code BETWEEN} directo en vez de un filtro null-safe.
     */
    @Query(value = "SELECT si.product_id, si.product_name, SUM(si.quantity), SUM(si.subtotal) " +
                   "FROM sale_items si JOIN sales s ON si.sale_id = s.id " +
                   "WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "AND (:tiendaId IS NULL OR s.tienda_id = :tiendaId) " +
                   "GROUP BY si.product_id, si.product_name " +
                   "ORDER BY SUM(si.quantity) DESC LIMIT :lim",
           nativeQuery = true)
    List<Object[]> topProductsBetween(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       @Param("lim") int limit,
                                       @Param("tiendaId") Long tiendaId);
}
