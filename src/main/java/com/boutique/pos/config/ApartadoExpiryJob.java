package com.boutique.pos.config;

import com.boutique.pos.service.ApartadoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado que revisa periódicamente los apartados {@code ACTIVE} vencidos y
 * restituye su stock — ver {@link ApartadoService#expireOverdue()} para el detalle de qué
 * hace exactamente. Mismo patrón que {@code CashCutAutoCloseJob} (un {@code @Scheduled}
 * simple con {@code fixedRate}), pero corre cada 5 minutos en vez de cada minuto: a
 * diferencia del cierre de caja (que debe dispararse en un minuto exacto), aquí no
 * importa vencer un apartado con unos minutos de retraso.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApartadoExpiryJob {

    private final ApartadoService apartadoService;

    @Scheduled(fixedRate = 5 * 60_000)
    public void checkAndRun() {
        int expired = apartadoService.expireOverdue();
        if (expired > 0) {
            log.info("Apartados vencidos: {} apartado(s) marcado(s) como EXPIRED y su stock restituido", expired);
        }
    }
}
