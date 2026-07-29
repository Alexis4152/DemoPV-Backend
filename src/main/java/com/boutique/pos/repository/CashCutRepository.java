package com.boutique.pos.repository;

import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.CashCut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CashCutRepository extends JpaRepository<CashCut, Long> {
    Optional<CashCut> findFirstByStatus(CashCutStatus status);
    Page<CashCut> findAllByOrderByOpenedAtDesc(Pageable pageable);
    boolean existsByUserIdAndOpenedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
    Optional<CashCut> findFirstByUserIdAndOpenedAtBetweenOrderByOpenedAtDesc(Long userId, LocalDateTime from, LocalDateTime to);
}
