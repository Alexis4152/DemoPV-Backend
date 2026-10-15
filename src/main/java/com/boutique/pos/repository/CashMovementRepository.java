package com.boutique.pos.repository;

import com.boutique.pos.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    /** Movimientos registrados dentro de un corte, en orden cronológico. */
    List<CashMovement> findByCashCut_IdOrderByCreatedAtAsc(Long cashCutId);

    /** Movimientos con la caja cerrada que todavía no recibe nadie (la entrega pendiente). */
    List<CashMovement> findByCashRegisterIdAndCashCutIsNullAndReceivedByCutIsNullOrderByCreatedAtAsc(Long cashRegisterId);

    /** Movimientos con la caja cerrada que vio un corte al recibir la caja. */
    List<CashMovement> findByReceivedByCutIdOrderByCreatedAtAsc(Long cutId);
}
