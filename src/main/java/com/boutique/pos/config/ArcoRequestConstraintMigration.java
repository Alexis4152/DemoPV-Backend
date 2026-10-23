package com.boutique.pos.config;

import com.boutique.pos.model.ArcoRequestStatus;
import com.boutique.pos.model.ArcoRequestType;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Mantiene al día las restricciones {@code arco_requests_status_check} y {@code
 * arco_requests_type_check}. Hibernate las crea con los valores de {@link
 * ArcoRequestStatus}/{@link ArcoRequestType} al crear la columna, pero {@code
 * ddl-auto=update} nunca las actualiza cuando el enum crece (ej. se agregó {@code
 * PENDING_EMAIL}/{@code INFO_REQUESTED}/... a status, y {@code REVOCATION}/{@code
 * LIMITATION} a type) — sin esto, guardar un valor nuevo truena con una violación de la
 * restricción. Las vuelve a crear en cada arranque con todos los valores actuales de cada
 * enum — idempotente. Mismo patrón que {@code CashMovementTypeConstraintMigration}.
 */
@Component
@Order(31)
@RequiredArgsConstructor
@Slf4j
public class ArcoRequestConstraintMigration implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        String statuses = Arrays.stream(ArcoRequestStatus.values())
                .map(s -> "'" + s.name() + "'")
                .collect(Collectors.joining(", "));
        entityManager.createNativeQuery("ALTER TABLE arco_requests DROP CONSTRAINT IF EXISTS arco_requests_status_check").executeUpdate();
        entityManager.createNativeQuery("ALTER TABLE arco_requests ADD CONSTRAINT arco_requests_status_check CHECK (status IN (" + statuses + "))").executeUpdate();

        String types = Arrays.stream(ArcoRequestType.values())
                .map(t -> "'" + t.name() + "'")
                .collect(Collectors.joining(", "));
        entityManager.createNativeQuery("ALTER TABLE arco_requests DROP CONSTRAINT IF EXISTS arco_requests_type_check").executeUpdate();
        entityManager.createNativeQuery("ALTER TABLE arco_requests ADD CONSTRAINT arco_requests_type_check CHECK (type IN (" + types + "))").executeUpdate();

        log.info("Restricciones arco_requests_status_check/arco_requests_type_check actualizadas: status=[{}], type=[{}]", statuses, types);
    }
}
