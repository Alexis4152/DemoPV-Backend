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
    }
}
