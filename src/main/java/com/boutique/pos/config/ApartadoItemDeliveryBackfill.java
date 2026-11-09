package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * La entrega se registraba solo en el apartado; ahora es por renglón (entregas parciales). Copia
 * la fecha y quién entregó a las líneas de los apartados ya entregados por completo. Solo toca
 * líneas sin fecha de entrega, así que es idempotente.
 */
@Component
@Order(34)
@RequiredArgsConstructor
@Slf4j
public class ApartadoItemDeliveryBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE apartado_items i SET delivered_at = a.delivered_at, delivered_by_user_id = a.delivered_by_user_id " +
                "FROM apartados a WHERE i.apartado_id = a.id AND a.delivered_at IS NOT NULL " +
                "AND i.delivered_at IS NULL AND COALESCE(a.pending_delivery, false) = false").executeUpdate();
        if (updated > 0) {
            log.info("Fecha de entrega copiada a {} productos de apartados ya entregados", updated);
        }
        // Liquidados (o completados en línea) sin "por entregar": se entregaron al cobrar.
        int paid = entityManager.createNativeQuery(
                "UPDATE apartado_items i SET delivered_at = s.created_at, delivered_by_user_id = a.completed_by_user_id " +
                "FROM apartados a JOIN sales s ON s.id = a.sale_id WHERE i.apartado_id = a.id " +
                "AND a.status = 'COMPLETED' AND COALESCE(a.pending_delivery, false) = false " +
                "AND a.delivered_at IS NULL AND i.delivered_at IS NULL").executeUpdate();
        if (paid > 0) {
            log.info("Productos de apartados liquidados marcados como entregados al cobrar: {}", paid);
        }
    }
}
