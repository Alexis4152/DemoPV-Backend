package com.boutique.pos.repository;

import com.boutique.pos.model.Role;
import com.boutique.pos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByOrderByNameAsc();
    long countByRole(Role role);

    // from/to siempre vienen con un valor real (nunca null) — ver UserService.findAll,
    // porque Postgres no logra inferir el tipo de un parámetro timestamp nulo.
    @Query("SELECT u FROM User u WHERE " +
           "(:tiendaId IS NULL OR u.tienda.id = :tiendaId) " +
           "AND u.createdAt BETWEEN :from AND :to " +
           "AND (:name IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:email AS string), '%'))) " +
           "AND (:roleId IS NULL OR u.role.id = :roleId) " +
           "AND (:isActive IS NULL OR u.isActive = :isActive) " +
           "ORDER BY u.name ASC")
    List<User> search(@Param("tiendaId") Long tiendaId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to,
                       @Param("name") String name,
                       @Param("email") String email,
                       @Param("roleId") Long roleId,
                       @Param("isActive") Boolean isActive);

    // para avisarle por correo al/los admin(es) de una tienda cuando se autocierra un corte
    @Query("SELECT u FROM User u WHERE u.tienda.id = :tiendaId AND u.role.name = 'ADMIN' AND u.isActive = true")
    List<User> findAdminsByTiendaId(@Param("tiendaId") Long tiendaId);
}
