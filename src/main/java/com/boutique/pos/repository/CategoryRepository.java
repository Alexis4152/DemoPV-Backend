package com.boutique.pos.repository;

import com.boutique.pos.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio de {@link Category}.
 *
 * <p>El borrado de una categoría es siempre suave (campo {@code isActive=false}, sin eliminar
 * la fila físicamente), por lo que ambas consultas de listado filtran explícitamente
 * {@code isActive = true} para no mostrar categorías eliminadas.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Lista todas las categorías activas (no eliminadas), ordenadas alfabéticamente. */
    List<Category> findAllByIsActiveTrueOrderByNameAsc();

    /**
     * Lista las categorías activas de una tienda, ordenadas alfabéticamente.
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por tienda);
     * cualquier otro valor restringe el resultado a esa tienda.
     */
    @Query("SELECT c FROM Category c WHERE c.isActive = true AND (:tiendaId IS NULL OR c.tienda.id = :tiendaId) ORDER BY c.name ASC")
    List<Category> findAllForTienda(@Param("tiendaId") Long tiendaId);

    // Excluye siempre las eliminadas (borrado suave): un nombre reutilizado por una
    // categoría ya desactivada no debe bloquear crear una nueva con ese mismo nombre.
    // excludeId es null al crear (nada que excluir) y el propio id al editar (para no
    // chocar contra sí misma si no cambió el nombre).
    /**
     * Indica si ya existe otra categoría ACTIVA con ese nombre (insensible a mayúsculas)
     * dentro de la tienda dada — usado para impedir duplicados al crear/editar.
     */
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.isActive = true AND c.tienda.id = :tiendaId " +
           "AND LOWER(c.name) = LOWER(:name) AND (:excludeId IS NULL OR c.id <> :excludeId)")
    boolean existsActiveByNameAndTienda(@Param("name") String name, @Param("tiendaId") Long tiendaId, @Param("excludeId") Long excludeId);

    // from/to siempre vienen con un valor real (nunca null) — mismo motivo que en
    // UserRepository.search: Postgres no logra inferir el tipo de un parámetro timestamp nulo.
    /**
     * Búsqueda paginada de categorías con filtros combinables, para la pantalla de
     * administración de Categorías (a diferencia de {@link #findAllForTienda}, pensada
     * para selectores que necesitan el catálogo completo sin paginar).
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas; {@code name}
     * hace coincidencia parcial insensible a mayúsculas (nulo = sin filtrar); {@code
     * isActive} es opcional (nulo = cualquier estado, a diferencia de {@link
     * #findAllForTienda} que siempre excluye las eliminadas).</p>
     */
    @Query("SELECT c FROM Category c WHERE " +
           "(:tiendaId IS NULL OR c.tienda.id = :tiendaId) " +
           "AND c.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:isActive IS NULL OR c.isActive = :isActive) " +
           "ORDER BY c.name ASC")
    Page<Category> search(@Param("tiendaId") Long tiendaId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("name") String name,
                           @Param("isActive") Boolean isActive,
                           Pageable pageable);
}
