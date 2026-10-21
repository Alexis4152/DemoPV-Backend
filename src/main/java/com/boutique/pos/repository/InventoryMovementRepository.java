package com.boutique.pos.repository;

import com.boutique.pos.model.InventoryMovement;
import com.boutique.pos.model.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de {@link InventoryMovement}, la bitácora de entradas/salidas de stock de un producto.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** Lista los movimientos de un producto, del más reciente al más antiguo. */
    List<InventoryMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    /** Lista todos los movimientos de inventario, del más reciente al más antiguo. */
    Page<InventoryMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Lista los movimientos de un producto dentro de un rango de fechas, del más reciente al más antiguo.
     *
     * <p>{@code from}/{@code to} deben llegar siempre con un valor real (nunca {@code null}), ya que
     * Postgres no puede inferir el tipo de un parámetro {@link LocalDateTime} nulo, por lo que aquí
     * se usa {@code BETWEEN} directo en vez del patrón {@code (:from IS NULL OR ...)}.
     */
    @Query("SELECT m FROM InventoryMovement m WHERE m.product.id = :productId AND m.createdAt BETWEEN :from AND :to ORDER BY m.createdAt DESC")
    List<Object[]> findByProductIdAndCreatedAtBetween(@Param("productId") Long productId,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);

    /**
     * Búsqueda paginada de movimientos de inventario de TODA la tienda (no de un solo
     * producto, a diferencia de {@link #findByProductIdAndCreatedAtBetween}), para el
     * reporte "Movimientos de inventario". {@code tiendaId} nulo indica SUPER_ADMIN viendo
     * todas las tiendas; {@code type} nulo no filtra. {@code from}/{@code to} siempre llegan
     * con un valor real (mismo motivo que el resto de los repos de reportes: Postgres no
     * puede inferir el tipo de un parámetro timestamp nulo).
     */
    @Query("SELECT m FROM InventoryMovement m WHERE " +
           "(:tiendaId IS NULL OR m.product.tienda.id = :tiendaId) " +
           "AND m.createdAt BETWEEN :from AND :to " +
           "AND (:type IS NULL OR m.type = :type) " +
           "ORDER BY m.createdAt DESC")
    Page<InventoryMovement> search(@Param("tiendaId") Long tiendaId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("type") MovementType type,
                                    Pageable pageable);
}
