package com.boutique.pos.repository;

import com.boutique.pos.model.ShortagePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShortagePaymentRepository extends JpaRepository<ShortagePayment, Long> {

    List<ShortagePayment> findByShortage_IdOrderByCreatedAtAsc(Long shortageId);

    /** Usado únicamente por {@code TiendaPurgeService} (borrado físico y total de una tienda): todos los pagos de sus faltantes. */
    void deleteByShortageTiendaId(Long tiendaId);
}
