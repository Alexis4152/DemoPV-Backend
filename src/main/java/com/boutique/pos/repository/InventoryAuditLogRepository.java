package com.boutique.pos.repository;

import com.boutique.pos.model.InventoryAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAuditLogRepository extends JpaRepository<InventoryAuditLog, Long> {

    /** Usado únicamente por {@code TiendaPurgeService} (borrado físico y total de una tienda). */
    void deleteByTiendaId(Long tiendaId);
}
