package com.boutique.pos.repository;

import com.boutique.pos.model.MaintenanceWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, Long> {

    /**
     * La ventana relevante AHORA MISMO: la más próxima a terminar entre las que todavía no
     * han terminado — es decir, la que está en curso (si hay una activa) o, si no, la
     * próxima programada. Las que ya terminaron quedan fuera sin necesidad de borrarlas
     * (quedan como historial).
     */
    Optional<MaintenanceWindow> findFirstByEndAtAfterOrderByStartAtAsc(LocalDateTime now);
}
