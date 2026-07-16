package com.boutique.pos.repository;

import com.boutique.pos.model.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    Page<InventoryMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT m FROM InventoryMovement m WHERE m.product.id = :productId AND m.createdAt BETWEEN :from AND :to ORDER BY m.createdAt DESC")
    List<Object[]> findByProductIdAndCreatedAtBetween(@Param("productId") Long productId,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);
}
