package com.boutique.pos.config;

import com.boutique.pos.model.InventoryAuditAction;
import com.boutique.pos.model.MovementSource;
import com.boutique.pos.model.MovementType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Mantiene al día las restricciones CHECK de las columnas enum de inventario. Hibernate las
 * crea con los valores del enum al crear la tabla/columna, pero {@code ddl-auto=update}
 * nunca las actualiza cuando el enum crece: sin esto, guardar un valor nuevo (p. ej. la
 * acción de bitácora) truena con una violación de la
 * restricción y tumba la operación completa. Mismo patrón que {@link
 * CashMovementTypeConstraintMigration}; se recrean en cada arranque — idempotente.
 */
@Component
@Order(34)
@RequiredArgsConstructor
@Slf4j
public class InventoryEnumConstraintMigration implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        refresh("inventory_audit_logs", "action", InventoryAuditAction.values());
        refresh("inventory_movements", "source", MovementSource.values());
        refresh("inventory_movements", "type", MovementType.values());
    }

    private void refresh(String table, String column, Enum<?>[] values) {
        String constraint = table + "_" + column + "_check";
        String list = Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
        entityManager.createNativeQuery("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + constraint).executeUpdate();
        // Un CHECK con IN deja pasar NULL (source es nulo en movimientos aún sin clasificar).
        entityManager.createNativeQuery("ALTER TABLE " + table + " ADD CONSTRAINT " + constraint
                + " CHECK (" + column + " IN (" + list + "))").executeUpdate();
        log.info("Restricción {} actualizada", constraint);
    }
}
