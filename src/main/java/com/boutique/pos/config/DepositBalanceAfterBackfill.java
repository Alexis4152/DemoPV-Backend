package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las ventas de anticipo/abono ahora guardan el saldo que le quedó al apartado después de ese
 * pago ({@code balance_after}), para imprimirlo en su ticket. Para las que ya existían se
 * calcula: total del apartado menos sus anticipos/abonos cobrados hasta ese momento.
 * Idempotente: solo toca las que aún no lo tienen.
 */
@Component
@Order(38)
@RequiredArgsConstructor
@Slf4j
public class DepositBalanceAfterBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE sales s SET balance_after = a.total - (" +
                "  SELECT COALESCE(SUM(p.total), 0) FROM sales p " +
                "  WHERE p.apartado_id = s.apartado_id AND p.kind = 'DEPOSIT' AND p.created_at <= s.created_at) " +
                "FROM apartados a " +
                "WHERE s.kind = 'DEPOSIT' AND s.balance_after IS NULL AND a.id = s.apartado_id AND a.total IS NOT NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Saldo pendiente calculado en ventas de anticipo/abono: {}", updated);
        }
    }
}
