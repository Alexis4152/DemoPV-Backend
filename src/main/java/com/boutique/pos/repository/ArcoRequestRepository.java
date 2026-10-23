package com.boutique.pos.repository;

import com.boutique.pos.model.ArcoRequest;
import com.boutique.pos.model.ArcoRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Repositorio de {@link ArcoRequest}, sin filtro de tienda — es plataforma completa. */
public interface ArcoRequestRepository extends JpaRepository<ArcoRequest, Long> {

    /**
     * Todas las solicitudes (o solo las del estado dado, si {@code status} no es nulo),
     * con las pendientes de atender siempre primero — para que al SUPER_ADMIN no se le
     * pierda una solicitud sin resolver entre las ya cerradas. Dentro de cada grupo, de más
     * reciente a más antigua.
     */
    @Query("SELECT a FROM ArcoRequest a WHERE (:status IS NULL OR a.status = :status) " +
           "ORDER BY CASE WHEN a.status IN (com.boutique.pos.model.ArcoRequestStatus.PENDING, " +
           "com.boutique.pos.model.ArcoRequestStatus.INFO_REQUESTED, " +
           "com.boutique.pos.model.ArcoRequestStatus.APPROVED) THEN 0 ELSE 1 END, a.createdAt DESC")
    List<ArcoRequest> search(@Param("status") ArcoRequestStatus status);

    /** Cuántas solicitudes siguen sin cerrar (PENDING/INFO_REQUESTED/APPROVED) — para el badge del menú. */
    @Query("SELECT COUNT(a) FROM ArcoRequest a WHERE a.status IN (" +
           "com.boutique.pos.model.ArcoRequestStatus.PENDING, " +
           "com.boutique.pos.model.ArcoRequestStatus.INFO_REQUESTED, " +
           "com.boutique.pos.model.ArcoRequestStatus.APPROVED)")
    long countOpen();

    /** Las solicitudes levantadas por una cuenta en particular ({@link ArcoRequest#getRequesterUser()}),
     *  de más reciente a más antigua — para "Mis solicitudes" (ver {@code ArcoRequestController#mine}). */
    List<ArcoRequest> findByRequesterUserIdOrderByCreatedAtDesc(Long requesterUserId);

    /** Resuelve una solicitud por el hash de su token de seguimiento (ver {@code ArcoRequestService#trackByToken}). */
    Optional<ArcoRequest> findByTrackingTokenHash(String trackingTokenHash);

    // ── Usadas por ArcoMaintenanceJob ────────────────────────────────────────────────────
    /** Solicitudes WEB que nunca confirmaron su correo, para purgarlas tras unos días. */
    List<ArcoRequest> findByStatusAndCreatedAtBefore(ArcoRequestStatus status, LocalDateTime before);

    /** Solicitudes esperando información del titular cuyo plazo de respuesta ya venció. */
    List<ArcoRequest> findByStatusAndInfoDueBefore(ArcoRequestStatus status, LocalDate before);

    @Modifying
    @Query("DELETE FROM ArcoRequest a WHERE a.status = :status AND a.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(@Param("status") ArcoRequestStatus status, @Param("before") LocalDateTime before);

    // ── Usadas únicamente por TiendaPurgeService (borrado FÍSICO y total de una tienda) ──
    // ArcoRequest es de PLATAFORMA (no se borra al borrar una tienda, ver su Javadoc): solo
    // se desvinculan sus referencias a los usuarios que sí se van a borrar.
    /** Desvincula de una tienda las solicitudes ARCO que la referencian como solicitante. */
    @Modifying
    @Query("UPDATE ArcoRequest a SET a.requesterUser = null WHERE a.requesterUser.tienda.id = :tiendaId")
    void clearRequesterUserByTiendaId(@Param("tiendaId") Long tiendaId);

    /** Desvincula de una tienda las solicitudes ARCO que la referencian como quien la resolvió. */
    @Modifying
    @Query("UPDATE ArcoRequest a SET a.resolvedBy = null WHERE a.resolvedBy.tienda.id = :tiendaId")
    void clearResolvedByByTiendaId(@Param("tiendaId") Long tiendaId);

    /** Desvincula de una tienda las solicitudes ARCO que la referencian como quien verificó la identidad. */
    @Modifying
    @Query("UPDATE ArcoRequest a SET a.identityVerifiedBy = null WHERE a.identityVerifiedBy.tienda.id = :tiendaId")
    void clearIdentityVerifiedByByTiendaId(@Param("tiendaId") Long tiendaId);

    /** Desvincula de una tienda las solicitudes ARCO que la referencian como quien la ejecutó. */
    @Modifying
    @Query("UPDATE ArcoRequest a SET a.executedBy = null WHERE a.executedBy.tienda.id = :tiendaId")
    void clearExecutedByByTiendaId(@Param("tiendaId") Long tiendaId);

    /** Desvincula de una tienda las solicitudes ARCO que la referencian como quien las registró en mostrador. */
    @Modifying
    @Query("UPDATE ArcoRequest a SET a.createdBy = null WHERE a.createdBy.tienda.id = :tiendaId")
    void clearCreatedByByTiendaId(@Param("tiendaId") Long tiendaId);
}
