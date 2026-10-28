package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los anticipos (apartados con {@code origin = DEPOSIT}) dejaron de vencer solos y ahora
 * llevan fecha de entrega. Los que ya estaban activos antes del cambio tienen
 * {@code expires_at} y vencerían con el job; aquí se toma esa fecha como su fecha de entrega
 * y se les quita el vencimiento. Solo toca anticipos activos sin fecha de entrega, así que
 * es idempotente.
 */
@Component
@Order(35)
@RequiredArgsConstructor
@Slf4j
public class DepositDeliveryDateBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE apartados SET delivery_date = CAST(expires_at AS date), expires_at = NULL " +
                "WHERE origin = 'DEPOSIT' AND status = 'ACTIVE' AND delivery_date IS NULL AND expires_at IS NOT NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Anticipos activos pasados a fecha de entrega (sin vencimiento): {}", updated);
        }
    }
}
