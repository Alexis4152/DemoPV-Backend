package com.boutique.pos.repository;

import com.boutique.pos.model.RawMaterial;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    /**
     * Materias primas activas con los mismos filtros que Inventario: texto en nombre o
     * código, categoría y stock bajo. {@code tiendaId} nulo = SUPER_ADMIN viendo todas.
     */
    @Query("SELECT m FROM RawMaterial m WHERE m.isActive = true " +
           "AND (:q IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%')) OR LOWER(m.barcode) LIKE LOWER(CONCAT('%',CAST(:q AS string),'%'))) " +
           "AND (:categoryId IS NULL OR m.category.id = :categoryId) " +
           "AND (:lowStock IS NULL OR (:lowStock = true AND m.stock <= m.minStock)) " +
           "AND (:tiendaId IS NULL OR m.tienda.id = :tiendaId) " +
           "ORDER BY m.name")
    Page<RawMaterial> search(@Param("q") String q,
                             @Param("categoryId") Long categoryId,
                             @Param("lowStock") Boolean lowStock,
                             @Param("tiendaId") Long tiendaId,
                             Pageable pageable);

    /** Materias primas activas en su mínimo o por debajo (aviso de stock bajo). */
    @Query("SELECT m FROM RawMaterial m WHERE m.isActive = true AND m.stock <= m.minStock " +
           "AND (:tiendaId IS NULL OR m.tienda.id = :tiendaId) ORDER BY m.stock ASC")
    List<RawMaterial> findLowStock(@Param("tiendaId") Long tiendaId);

    @Query("SELECT COUNT(m) > 0 FROM RawMaterial m WHERE m.isActive = true AND m.barcode = :barcode " +
           "AND m.tienda.id = :tiendaId AND (:excludeId IS NULL OR m.id <> :excludeId)")
    boolean existsActiveBarcode(@Param("barcode") String barcode, @Param("tiendaId") Long tiendaId, @Param("excludeId") Long excludeId);

    /** Igual que en productos: lock antes de leer/modificar el stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM RawMaterial m WHERE m.id = :id")
    Optional<RawMaterial> findByIdForUpdate(@Param("id") Long id);

    /** Usado únicamente por {@code TiendaPurgeService} (borrado físico y total de una tienda). */
    void deleteByTiendaId(Long tiendaId);
}
