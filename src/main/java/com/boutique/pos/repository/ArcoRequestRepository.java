package com.boutique.pos.repository;

import com.boutique.pos.model.ArcoRequest;
import com.boutique.pos.model.ArcoRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Repositorio de {@link ArcoRequest}, sin filtro de tienda — es plataforma completa. */
public interface ArcoRequestRepository extends JpaRepository<ArcoRequest, Long> {

    /**
     * Todas las solicitudes (o solo las del estado dado, si {@code status} no es nulo),
     * con las pendientes siempre primero — para que al SUPER_ADMIN no se le pierda una
     * solicitud sin atender entre las ya resueltas. Dentro de cada grupo, de más reciente
     * a más antigua.
     */
    @Query("SELECT a FROM ArcoRequest a WHERE (:status IS NULL OR a.status = :status) " +
           "ORDER BY CASE WHEN a.status = com.boutique.pos.model.ArcoRequestStatus.PENDING THEN 0 ELSE 1 END, a.createdAt DESC")
    List<ArcoRequest> search(@Param("status") ArcoRequestStatus status);

    /** Cuántas solicitudes siguen sin atender — para un badge en el menú, igual que el de apartados. */
    long countByStatus(ArcoRequestStatus status);

    /** Las solicitudes levantadas por una cuenta en particular ({@link ArcoRequest#getRequesterUser()}),
     *  de más reciente a más antigua — para "Mis solicitudes" (ver {@code ArcoRequestController#mine}). */
    List<ArcoRequest> findByRequesterUserIdOrderByCreatedAtDesc(Long requesterUserId);
}
