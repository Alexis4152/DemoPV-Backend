package com.boutique.pos.repository;

import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.CashCut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CashCutRepository extends JpaRepository<CashCut, Long> {
    Optional<CashCut> findFirstByStatus(CashCutStatus status);
    Page<CashCut> findAllByOrderByOpenedAtDesc(Pageable pageable);
    boolean existsByUserIdAndOpenedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
    Optional<CashCut> findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(Long userId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT c FROM CashCut c WHERE (:tiendaId IS NULL OR c.tienda.id = :tiendaId) ORDER BY c.openedAt DESC")
    Page<CashCut> findAllForTienda(@Param("tiendaId") Long tiendaId, Pageable pageable);

    // el "ya hay un corte abierto" es por tienda, no global — así que un null de tiendaId
    // (SUPER_ADMIN sin tienda) se compara contra cortes que tampoco tengan tienda.
    @Query("SELECT c FROM CashCut c WHERE c.status = :status AND " +
           "((:tiendaId IS NULL AND c.tienda IS NULL) OR c.tienda.id = :tiendaId)")
    Optional<CashCut> findFirstByStatusAndTiendaId(@Param("status") CashCutStatus status, @Param("tiendaId") Long tiendaId);
}
