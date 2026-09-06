package com.boutique.pos.repository;

import com.boutique.pos.model.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link Tienda}, la entidad raíz del aislamiento multi-tienda del sistema.
 * El borrado de una tienda es siempre suave (campo {@code isActive=false}).
 */
public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    /** Lista todas las tiendas registradas, ordenadas alfabéticamente. */
    List<Tienda> findAllByOrderByNameAsc();

    /**
     * Busca una tienda activa por su slug público (ej. "mi-tienda"), usado por {@code
     * PublicController} para resolver qué catálogo mostrar en {@code /apartar/{slug}} —
     * es lo único que identifica a la tienda en esa superficie, sin login de cliente.
     */
    Optional<Tienda> findByPublicSlugAndIsActiveTrue(String publicSlug);

    /** Verifica si un slug ya está en uso por otra tienda (validación de unicidad al editarlo). */
    boolean existsByPublicSlugIgnoreCaseAndIdNot(String publicSlug, Long id);
}
