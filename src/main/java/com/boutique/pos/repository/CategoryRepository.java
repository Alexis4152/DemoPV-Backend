package com.boutique.pos.repository;

import com.boutique.pos.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderByNameAsc();

    @Query("SELECT c FROM Category c WHERE (:tiendaId IS NULL OR c.tienda.id = :tiendaId) ORDER BY c.name ASC")
    List<Category> findAllForTienda(@Param("tiendaId") Long tiendaId);
}
