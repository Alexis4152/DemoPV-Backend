package com.boutique.pos.repository;

import com.boutique.pos.model.RawMaterialMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawMaterialMovementRepository extends JpaRepository<RawMaterialMovement, Long> {

    /** Historial de una materia prima, del más reciente al más antiguo. */
    @EntityGraph(attributePaths = {"user", "product"})
    Page<RawMaterialMovement> findByRawMaterial_IdOrderByCreatedAtDescIdDesc(Long rawMaterialId, Pageable pageable);

    /** Usado únicamente por {@code TiendaPurgeService} (borrado físico y total de una tienda): todos los movimientos de sus materias primas. */
    void deleteByRawMaterialTiendaId(Long tiendaId);
}
