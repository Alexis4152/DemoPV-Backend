package com.boutique.pos.repository;

import com.boutique.pos.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

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
