package com.boutique.pos.repository;

import com.boutique.pos.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link Role}.
 *
 * <p>A diferencia de la mayoría de las entidades del dominio, un {@link Role} no es global:
 * pertenece a una sola tienda (campo {@code tienda}) salvo los roles de plataforma
 * (por ejemplo SUPER_ADMIN) o los templates originales previos a la migración multi-tienda,
 * donde {@code tienda} es {@code null}. El borrado de un rol es siempre suave
 * (campo {@code isActive=false}), por lo que los listados usados en la UI filtran
 * explícitamente {@code isActive = true} para no mostrar roles eliminados.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
    // tienda-less (SUPER_ADMIN, o los templates originales antes de migrarlos)
    /** Busca un rol sin tienda (de plataforma o template) por nombre. */
    Optional<Role> findByName(String name);
    /** Busca el primer rol sin tienda con ese nombre, ordenado por id; usado cuando puede haber duplicados de un mismo template. */
    Optional<Role> findFirstByNameOrderById(String name);
    // A diferencia de la anterior, esta SÍ exige tienda_id IS NULL explícitamente — hace
    // falta porque el nombre de un rol de sistema (SUPER_ADMIN, SUPERVISOR) no es único
    // globalmente: cualquier tienda puede tener su propio rol personalizado con ESE MISMO
    // nombre (ver RoleService#create, que solo valida unicidad por tienda) sin que eso
    // choque con el rol de plataforma real. Usada por RoleDataInitializer para no
    // confundir un rol de tienda que coincide de nombre con el rol de sistema ya sembrado.
    /** Busca el rol DE PLATAFORMA (sin tienda) con ese nombre exacto, ordenado por id. */
    Optional<Role> findFirstByNameAndTiendaIsNullOrderById(String name);
    /** Lista todos los roles del sistema, ordenados alfabéticamente. */
    List<Role> findAllByOrderByNameAsc();
    /** Lista los roles sin tienda asignada (roles de plataforma o templates originales). */
    List<Role> findAllByTiendaIsNull();

    /** Busca el rol de una tienda por nombre. */
    Optional<Role> findByNameAndTiendaId(String name, Long tiendaId);
    /** Indica si ya existe un rol con ese nombre dentro de la tienda dada (unicidad por tienda+nombre). */
    boolean existsByNameAndTiendaId(String name, Long tiendaId);

    // listados para la pantalla de Roles y el selector de rol al dar de alta un usuario —
    // no deben mostrar roles eliminados (borrado suave, ver isActive).
    /** Lista todos los roles activos (no eliminados) del sistema, ordenados alfabéticamente. */
    List<Role> findAllByIsActiveTrueOrderByNameAsc();
    /** Lista los roles activos (no eliminados) de una tienda, ordenados alfabéticamente. */
    List<Role> findAllByTiendaIdAndIsActiveTrueOrderByNameAsc(Long tiendaId);

    // mismos filtros que las dos anteriores, pero paginados — usados por la pantalla de
    // administración de Roles (a diferencia de las de arriba, que alimentan selectores que
    // necesitan el catálogo completo, ej. el filtro/formulario de Usuarios).
    /** Página de roles activos (no eliminados) del sistema, ordenados alfabéticamente. */
    Page<Role> findAllByIsActiveTrueOrderByNameAsc(Pageable pageable);
    /** Página de roles activos (no eliminados) de una tienda, ordenados alfabéticamente. */
    Page<Role> findAllByTiendaIdAndIsActiveTrueOrderByNameAsc(Long tiendaId, Pageable pageable);
}
