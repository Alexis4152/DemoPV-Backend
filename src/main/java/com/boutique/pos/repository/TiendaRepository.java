package com.boutique.pos.repository;

import com.boutique.pos.model.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio de {@link Tienda}, la entidad raíz del aislamiento multi-tienda del sistema.
 * El borrado de una tienda es siempre suave (campo {@code isActive=false}).
 */
public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    /** Lista todas las tiendas registradas, ordenadas alfabéticamente. */
    List<Tienda> findAllByOrderByNameAsc();
}
