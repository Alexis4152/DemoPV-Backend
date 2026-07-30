package com.boutique.pos.repository;

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

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("SELECT s FROM Sale s WHERE (:tiendaId IS NULL OR s.tienda.id = :tiendaId) ORDER BY s.createdAt DESC")
    Page<Sale> findAllForTienda(@Param("tiendaId") Long tiendaId, Pageable pageable);

    @Query("SELECT s FROM Sale s WHERE s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId) ORDER BY s.createdAt DESC")
    Page<Sale> findBetweenForTienda(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                     @Param("tiendaId") Long tiendaId, Pageable pageable);

    List<Sale> findByCashCutId(Long cashCutId);

    @Query("SELECT COALESCE(SUM(s.total),0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId)")
    BigDecimal totalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId)")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    @Query("SELECT CAST(s.createdAt AS date), COALESCE(SUM(s.total),0), COUNT(s) " +
           "FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "AND (:tiendaId IS NULL OR s.tienda.id = :tiendaId) " +
           "GROUP BY CAST(s.createdAt AS date) ORDER BY CAST(s.createdAt AS date)")
    List<Object[]> salesByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("tiendaId") Long tiendaId);

    @Query("SELECT p.id, p.name, p.stock, p.minStock FROM Product p WHERE p.isActive = true AND p.stock <= p.minStock " +
           "AND (:tiendaId IS NULL OR p.tienda.id = :tiendaId) ORDER BY p.stock ASC")
    List<Object[]> lowStockProducts(@Param("tiendaId") Long tiendaId);
}
