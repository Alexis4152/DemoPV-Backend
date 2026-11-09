package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clasifica el origen ({@code inventory_movements.source}, ver {@link
 * com.boutique.pos.model.MovementSource}) de los movimientos registrados antes de que
 * existiera ese campo, a partir del texto de su motivo — que cada flujo siempre escribió
 * igual ("Venta", "Stock inicial", "Apartado #N confirmado"...). Solo toca filas con
 * {@code source} nulo, así que es idempotente y no hace nada una vez clasificadas.
 */
@Component
@Order(33)
@RequiredArgsConstructor
@Slf4j
public class MovementSourceBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE inventory_movements SET source = CASE " +
                "  WHEN type = 'SALE' THEN 'SALE' " +
                "  WHEN reason LIKE 'Cancelación de venta%' THEN 'SALE_CANCEL' " +
                "  WHEN reason LIKE 'Apartado #% confirmado' THEN 'APARTADO' " +
                // Salida al registrar un anticipo (antes se guardaba sin origen): es APARTADO,
                // no un regreso — va antes de la regla general de 'Apartado #%'.
                "  WHEN reason LIKE 'Apartado #% (anticipo)' THEN 'APARTADO' " +
                "  WHEN reason LIKE 'Apartado #%' THEN 'APARTADO_RETURN' " +
                "  WHEN reason = 'Stock inicial' THEN 'INITIAL_STOCK' " +
                "  WHEN reason LIKE 'Carga de existencias%' THEN 'STOCK_IMPORT' " +
                "  WHEN reason LIKE 'Desde materia prima%' THEN 'RAW_MATERIAL' " +
                "  WHEN new_stock < previous_stock AND LOWER(COALESCE(reason, '')) LIKE '%merma%' THEN 'MERMA' " +
                "  WHEN new_stock < previous_stock THEN 'MANUAL_OUT' " +
                "  ELSE 'MANUAL_IN' END " +
                "WHERE source IS NULL").executeUpdate();
        if (updated > 0) {
            log.info("Origen clasificado en {} movimientos de inventario anteriores", updated);
        }
        // Corrección: la regla general 'Apartado #%' clasificó como regreso (APARTADO_RETURN) la
        // SALIDA de stock al registrar un anticipo. Idempotente: solo toca esas salidas.
        int fixed = entityManager.createNativeQuery(
                "UPDATE inventory_movements SET source = 'APARTADO' " +
                "WHERE source = 'APARTADO_RETURN' AND type = 'OUT' AND reason LIKE 'Apartado #% (anticipo)'")
                .executeUpdate();
        if (fixed > 0) {
            log.info("Salidas por anticipo reclasificadas como Apartado: {}", fixed);
        }
        // La salida de lo sobre pedido al liquidar o al entregar es parte de la entrega.
        int delivered = entityManager.createNativeQuery(
                "UPDATE inventory_movements SET source = 'APARTADO_DELIVERED' " +
                "WHERE source = 'APARTADO' AND type = 'OUT' AND reason LIKE 'Apartado #% (sobre pedido, %)'")
                .executeUpdate();
        if (delivered > 0) {
            log.info("Salidas sobre pedido reclasificadas como Apartado entregado: {}", delivered);
        }
    }
}
