package com.boutique.pos.config;

import com.boutique.pos.service.ArcoRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado de mantenimiento de solicitudes ARCO — ver {@link
 * ArcoRequestService#maintenance()}: purga las que nunca confirmaron su correo (canal WEB,
 * más de 3 días) y cierra como {@code NOT_FILED} las que esperan información del titular
 * cuyo plazo ya venció. Mismo patrón que {@code TokenPurgeJob}/{@code ApartadoExpiryJob},
 * corre una vez por hora.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArcoMaintenanceJob {

    private final ArcoRequestService arcoRequestService;

    @Scheduled(fixedRate = 60 * 60_000)
    public void run() {
        int touched = arcoRequestService.maintenance();
        if (touched > 0) {
            log.info("Mantenimiento de solicitudes ARCO: {} fila(s) afectada(s)", touched);
        }
    }
}
