package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las salidas de caja por devolución ({@code REFUND}) ahora guardan de qué venta son
 * ({@code sale_id}). Para las que ya existían se busca la venta devuelta desde ese mismo corte,
 * por el mismo monto y en el mismo momento (se registran juntas). Idempotente: solo toca las
 * que aún no tienen venta.
 */
@Component
@Order(37)
@RequiredArgsConstructor
@Slf4j
public class RefundMovementSaleBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE cash_movements m SET sale_id = s.id FROM sales s " +
                "WHERE m.type = 'REFUND' AND m.sale_id IS NULL " +
                "AND s.refund_cash_cut_id = m.cash_cut_id AND s.total = m.amount " +
                "AND ABS(EXTRACT(EPOCH FROM (s.refunded_at - m.created_at))) < 10")
                .executeUpdate();
        if (updated > 0) {
            log.info("Devoluciones en caja ligadas a su venta: {}", updated);
        }
    }
}
