package com.boutique.pos.repository;

import com.boutique.pos.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByIsActiveTrueOrderByNameAsc();

    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
           "AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.barcode) LIKE LOWER(CONCAT('%',:q,'%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:lowStock IS NULL OR (:lowStock = true AND p.stock <= p.minStock))")
    Page<Product> searchActive(@Param("q") String q,
                                @Param("categoryId") Long categoryId,
                                @Param("lowStock") Boolean lowStock,
                                Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.stock <= p.minStock ORDER BY p.stock ASC")
    List<Product> findLowStock();
}
