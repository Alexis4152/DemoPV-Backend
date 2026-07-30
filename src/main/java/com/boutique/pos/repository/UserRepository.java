package com.boutique.pos.repository;

import com.boutique.pos.model.Role;
import com.boutique.pos.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByOrderByNameAsc();
    long countByRole(Role role);

    @Query("SELECT u FROM User u WHERE (:tiendaId IS NULL OR u.tienda.id = :tiendaId) ORDER BY u.name ASC")
    List<User> findAllForTienda(@Param("tiendaId") Long tiendaId);
}
