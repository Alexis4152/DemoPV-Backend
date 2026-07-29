package com.boutique.pos.config;

import com.boutique.pos.model.Tienda;
import com.boutique.pos.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Runs after RoleDataInitializer so SUPER_ADMIN and legacy-role migration are already in place.
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class TenantDataInitializer implements CommandLineRunner {

    private static final List<String> TABLES_TO_BACKFILL = List.of("products", "categories", "sales", "cash_cuts");

    private final TiendaRepository tiendaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        Tienda defaultTienda = ensureDefaultTienda();
        backfillTiendaId(defaultTienda);
    }

    private Tienda ensureDefaultTienda() {
        if (tiendaRepository.count() > 0) {
            return tiendaRepository.findAllByOrderByNameAsc().get(0);
        }
        Tienda tienda = new Tienda();
        tienda.setName("Tienda Principal");
        tienda.setIsActive(true);
        tienda = tiendaRepository.save(tienda);
        log.info("Tienda por defecto creada: {}", tienda.getName());
        return tienda;
    }

    private void backfillTiendaId(Tienda defaultTienda) {
        for (String table : TABLES_TO_BACKFILL) {
            int updated = jdbcTemplate.update(
                    "UPDATE " + table + " SET tienda_id = ? WHERE tienda_id IS NULL", defaultTienda.getId());
            if (updated > 0) {
                log.info("Migrados {} registros de '{}' a la tienda por defecto", updated, table);
            }
        }
        // Users are handled separately: SUPER_ADMIN accounts must stay tienda-less,
        // everyone else without a tienda yet is assigned to the default one.
        int updatedUsers = jdbcTemplate.update(
                "UPDATE users SET tienda_id = ? WHERE tienda_id IS NULL " +
                        "AND role_id NOT IN (SELECT id FROM roles WHERE name = 'SUPER_ADMIN')",
                defaultTienda.getId());
        if (updatedUsers > 0) {
            log.info("Migrados {} usuarios a la tienda por defecto", updatedUsers);
        }
    }
}
