package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las ventas de saldo de un anticipo ({@code kind = BALANCE}) guardaban el anticipo sumado al
 * descuento. Ahora va aparte ({@code deposit_applied}): aquí se saca del descuento en las que
 * ya existían. Solo toca las que aún no tienen {@code deposit_applied}, así que es idempotente.
 */
@Component
@Order(36)
@RequiredArgsConstructor
@Slf4j
public class BalanceSaleDepositBackfill implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE sales s SET deposit_applied = a.deposit_amount, deposit_sale_folio = a.deposit_sale_folio, " +
                "discount = GREATEST(s.discount - a.deposit_amount, 0) " +
                "FROM apartados a " +
                "WHERE s.kind = 'BALANCE' AND s.apartado_id = a.id AND s.deposit_applied IS NULL " +
                "AND a.deposit_amount IS NOT NULL")
                .executeUpdate();
        if (updated > 0) {
            log.info("Ventas de saldo con el anticipo separado del descuento: {}", updated);
        }
    }
}
