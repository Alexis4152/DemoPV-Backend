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

    /**
     * Rentabilidad (ingreso menos costo) por producto dentro de un rango de fechas,
     * devolviendo {@code [productId, productName, ingreso, costoEstimado, margen]} ordenado
     * de mayor a menor margen, limitado a {@code limit} productos.
     *
     * <p><b>Importante:</b> el costo usado es el {@code cost} ACTUAL del producto en el
     * catálogo, no el que tenía en el momento de cada venta (a diferencia de
     * {@code unitPrice}, el costo no se "fotografía" por línea de venta). Si el costo de un
     * producto cambió después de venderse, el margen mostrado aquí es una estimación con el
     * costo de hoy, no el margen histórico exacto de esa venta. Mismo patrón de filtros que
     * {@link #topProductsBetween}.</p>
     */
    @Query(value = "SELECT si.product_id, si.product_name, SUM(si.subtotal), " +
                   "SUM(si.quantity * COALESCE(p.cost, 0)), " +
                   "SUM(si.subtotal) - SUM(si.quantity * COALESCE(p.cost, 0)) AS margin " +
                   "FROM sale_items si " +
                   "JOIN sales s ON si.sale_id = s.id " +
                   "JOIN products p ON si.product_id = p.id " +
                   "WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "AND (:tiendaId IS NULL OR s.tienda_id = :tiendaId) " +
                   "GROUP BY si.product_id, si.product_name " +
                   "ORDER BY margin DESC LIMIT :lim",
           nativeQuery = true)
    List<Object[]> topProductsByMargin(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("lim") int limit,
                                        @Param("tiendaId") Long tiendaId);

    /**
     * Total vendido por categoría de producto dentro de un rango de fechas, devolviendo
     * {@code [categoryId, categoryName, cantidadVendida, totalVendido]} ordenado de mayor a
     * menor monto. Solo incluye renglones cuyo producto siga existiendo y tenga categoría
     * asignada (un producto no puede quedar sin categoría por diseño, ver
     * {@code ProductService}). Mismo patrón de filtros que {@link #topProductsBetween}.
     */
    @Query(value = "SELECT c.id, c.name, SUM(si.quantity), SUM(si.subtotal) " +
                   "FROM sale_items si " +
                   "JOIN sales s ON si.sale_id = s.id " +
                   "JOIN products p ON si.product_id = p.id " +
                   "JOIN categories c ON p.category_id = c.id " +
                   "WHERE s.status = 'COMPLETED' AND s.created_at BETWEEN :from AND :to " +
                   "AND (:tiendaId IS NULL OR s.tienda_id = :tiendaId) " +
                   "GROUP BY c.id, c.name ORDER BY SUM(si.subtotal) DESC",
           nativeQuery = true)
    List<Object[]> salesByCategory(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("tiendaId") Long tiendaId);
}
