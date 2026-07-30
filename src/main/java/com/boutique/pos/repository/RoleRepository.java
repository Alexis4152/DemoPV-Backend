package com.boutique.pos.repository;

import com.boutique.pos.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    // tienda-less (SUPER_ADMIN, o los templates originales antes de migrarlos)
    Optional<Role> findByName(String name);
    Optional<Role> findFirstByNameOrderById(String name);
    List<Role> findAllByOrderByNameAsc();
    List<Role> findAllByTiendaIsNull();

    Optional<Role> findByNameAndTiendaId(String name, Long tiendaId);
    boolean existsByNameAndTiendaId(String name, Long tiendaId);
    List<Role> findAllByTiendaIdOrderByNameAsc(Long tiendaId);
}
