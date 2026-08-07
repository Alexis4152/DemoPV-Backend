package com.boutique.pos.repository;

import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.CashCut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashCutRepository extends JpaRepository<CashCut, Long> {
    Page<CashCut> findAllByOrderByOpenedAtDesc(Pageable pageable);
    boolean existsByUserIdAndOpenedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
    Optional<CashCut> findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(Long userId, LocalDateTime from, LocalDateTime to);

    // el corte propio de cada quien — varios pueden estar OPEN a la vez en la misma tienda
    Optional<CashCut> findFirstByUserIdAndStatus(Long userId, CashCutStatus status);

    // usado por el job de cierre automático: todos los cortes abiertos, de cualquier tienda
    List<CashCut> findAllByStatus(CashCutStatus status);

    // usado por el job de cierre automático para armar el reporte del día completo:
    // todos los cortes ya cerrados hoy, sin importar si se cerraron a mano antes o los cerró el job.
    List<CashCut> findAllByStatusAndOpenedAtBetween(CashCutStatus status, LocalDateTime from, LocalDateTime to);

    // from/to siempre vienen con un valor real (nunca null) — ver CashCutService.findAll.
    @Query("SELECT c FROM CashCut c WHERE " +
           "(:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "AND c.openedAt BETWEEN :from AND :to " +
           "AND (:status IS NULL OR c.status = :status) " +
           "ORDER BY c.openedAt DESC")
    Page<CashCut> search(@Param("tiendaId") Long tiendaId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("status") CashCutStatus status,
                          Pageable pageable);
}
