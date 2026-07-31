package com.boutique.pos.repository;

import com.boutique.pos.model.CashCutSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashCutScheduleRepository extends JpaRepository<CashCutSchedule, Long> {
}
