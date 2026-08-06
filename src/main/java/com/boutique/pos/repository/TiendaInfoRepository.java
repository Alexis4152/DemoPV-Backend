package com.boutique.pos.repository;

import com.boutique.pos.model.TiendaInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TiendaInfoRepository extends JpaRepository<TiendaInfo, Long> {
    Optional<TiendaInfo> findByTiendaId(Long tiendaId);
}
