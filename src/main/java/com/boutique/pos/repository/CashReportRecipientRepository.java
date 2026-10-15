package com.boutique.pos.repository;

import com.boutique.pos.model.CashReportRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashReportRecipientRepository extends JpaRepository<CashReportRecipient, Long> {

    List<CashReportRecipient> findByTiendaIdOrderByIdAsc(Long tiendaId);

    void deleteByTiendaId(Long tiendaId);
}
