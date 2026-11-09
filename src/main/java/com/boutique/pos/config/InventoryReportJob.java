package com.boutique.pos.config;

import com.boutique.pos.model.Tienda;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.service.InventoryReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Revisa cada minuto qué tiendas ya llegaron a su hora del reporte de inventario, igual que
 * {@link CashCutAutoCloseJob}, pero con un horario por tienda.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReportJob {

    private final TiendaRepository tiendaRepository;
    private final InventoryReportService reportService;

    @Scheduled(fixedRate = 60_000)
    public void checkAndRun() {
        LocalDateTime now = LocalDateTime.now();
        for (Tienda tienda : tiendaRepository.findAllByOrderByNameAsc()) {
            if (tienda.getInventoryReportTime() == null || !Boolean.TRUE.equals(tienda.getIsActive())) continue;
            try {
                reportService.sendIfDue(tienda.getId(), now);
            } catch (RuntimeException e) {
                log.error("No se pudo mandar el reporte de inventario de {}: {}", tienda.getName(), e.getMessage());
            }
        }
    }
}
