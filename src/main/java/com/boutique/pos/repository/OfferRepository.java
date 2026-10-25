package com.boutique.pos.repository;

import com.boutique.pos.model.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de {@link Offer}.
 *
 * <p>El borrado de una oferta es siempre suave ({@code isActive=false}), por lo que el
 * listado filtra explícitamente {@code isActive = true} para no mostrar ofertas eliminadas.
 */
public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Query("SELECT o FROM Offer o WHERE o.isActive = true AND (:tiendaId IS NULL OR o.tienda.id = :tiendaId) ORDER BY o.name ASC")
    List<Offer> findAllForTienda(@Param("tiendaId") Long tiendaId);

    @Query("SELECT o FROM Offer o WHERE (:tiendaId IS NULL OR o.tienda.id = :tiendaId) " +
           "AND o.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:isActive IS NULL OR o.isActive = :isActive) " +
           "ORDER BY o.name ASC")
    Page<Offer> search(@Param("tiendaId") Long tiendaId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("name") String name,
                        @Param("isActive") Boolean isActive,
                        Pageable pageable);

    // Usada por SaleService al vender: todas las ofertas ACTIVAS ligadas a ese producto (lo
    // normal es que haya a lo más una vigente, pero si hubiera varias se toma la primera —
    // ver SaleService#findActiveOfferForProduct). No filtra por fecha aquí: eso lo valida
    // Offer#isCurrentlyActive en memoria, para no duplicar esa lógica en JPQL.
    @Query("SELECT o FROM Offer o JOIN o.products p WHERE p.id = :productId AND o.isActive = true")
    List<Offer> findActiveByProductId(@Param("productId") Long productId);

    /** Usado únicamente por {@code TiendaPurgeService} (borrado físico y total de una tienda). */
    void deleteByTiendaId(Long tiendaId);

    // Nativa porque la tabla puente offer_products no tiene entidad JPA propia (es un
    // @ManyToMany @JoinTable de Offer) — debe correr ANTES de borrar las ofertas o los
    // productos de la tienda en TiendaPurgeService, o la FK de offer_products hacia
    // cualquiera de las dos truena (mismo tipo de bug que ya tuvimos con la purga de
    // tienda y los roles cruzados: ver UserService#reconcileRoleForTienda).
    @Modifying
    @Query(value = "DELETE FROM offer_products WHERE offer_id IN (SELECT id FROM offers WHERE tienda_id = :tiendaId)", nativeQuery = true)
    void deleteOfferProductLinksByTiendaId(@Param("tiendaId") Long tiendaId);
}
