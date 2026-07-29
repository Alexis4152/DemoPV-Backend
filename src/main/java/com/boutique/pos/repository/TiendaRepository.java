package com.boutique.pos.repository;

import com.boutique.pos.model.Tienda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    List<Tienda> findAllByOrderByNameAsc();
}
