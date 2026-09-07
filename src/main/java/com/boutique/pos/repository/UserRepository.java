package com.boutique.pos.repository;

import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.Role;
import com.boutique.pos.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@link User}.
 *
 * <p>El borrado de un usuario es siempre suave (campo {@code isActive=false}); ver también
 * los campos de auditoría {@code createdBy}/{@code updatedBy}/{@code deletedBy} de la entidad,
 * cargados en {@code FetchType.LAZY} a propósito para no encadenar el join
 * {@code User → createdBy(User) → createdBy(User) → ...} sin límite, que provocaba que Postgres
 * tronara por límite de profundidad de stack.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /** Busca un usuario por su correo electrónico (usado en el login). */
    Optional<User> findByEmail(String email);
    /** Indica si ya existe un usuario registrado con ese correo. */
    boolean existsByEmail(String email);
    /** Lista todos los usuarios, ordenados alfabéticamente por nombre. */
    List<User> findAllByOrderByNameAsc();
    /** Cuenta cuántos usuarios tienen asignado el rol dado (usado para validar antes de eliminar/desactivar un rol). */
    long countByRole(Role role);

    // from/to siempre vienen con un valor real (nunca null) — ver UserService.findAll,
    // porque Postgres no logra inferir el tipo de un parámetro timestamp nulo.
    /**
     * Búsqueda de usuarios con filtros combinables (usada en la pantalla de administración de usuarios).
     *
     * <p>{@code tiendaId} nulo indica SUPER_ADMIN viendo todas las tiendas (no se filtra por
     * tienda); cualquier otro valor restringe el resultado a esa tienda. {@code name}/{@code email}
     * hacen coincidencia parcial insensible a mayúsculas (nulo = sin filtrar); {@code roleId} e
     * {@code isActive} son opcionales (nulo = cualquier valor). {@code from}/{@code to} siempre
     * llegan con un valor real desde el service (nunca {@code null}), por la limitación de Postgres
     * para inferir el tipo de un parámetro timestamp nulo (ver comentario arriba).
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:tiendaId IS NULL OR u.tienda.id = :tiendaId) " +
           "AND u.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%'))) " +
           "AND (:roleId IS NULL OR u.role.id = :roleId) " +
           "AND (:isActive IS NULL OR u.isActive = :isActive) " +
           "ORDER BY u.name ASC")
    Page<User> search(@Param("tiendaId") Long tiendaId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("name") String name,
                       @Param("email") String email,
                       @Param("roleId") Long roleId,
                       @Param("isActive") Boolean isActive,
                       Pageable pageable);

    // para avisarle por correo al/los admin(es) de una tienda cuando se autocierra un corte
    /** Lista los usuarios activos con rol ADMIN de una tienda; usado para notificarles por correo cuando el job autocierra un corte. */
    @Query("SELECT u FROM User u WHERE u.tienda.id = :tiendaId AND u.role.name = 'ADMIN' AND u.isActive = true")
    List<User> findAdminsByTiendaId(@Param("tiendaId") Long tiendaId);

    // Para los avisos de apartados (nuevo/confirmado/cancelado/completado/vencido): a
    // diferencia de findAdminsByTiendaId (ADMIN nada más), esto trae a TODO el personal
    // activo con acceso a la sección dada, sin importar su rol — un cajero o vendedor con
    // la sección APARTADOS habilitada también necesita enterarse, no solo el admin.
    /**
     * Lista los usuarios activos de una tienda cuyo rol tiene habilitada la {@link
     * AppSection} dada — usado para avisos por correo dirigidos a "todo el personal que
     * puede actuar sobre esto", no solo a los administradores.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.role.sections s " +
           "WHERE u.tienda.id = :tiendaId AND u.isActive = true AND s = :section")
    List<User> findActiveByTiendaIdAndSection(@Param("tiendaId") Long tiendaId, @Param("section") AppSection section);
}
