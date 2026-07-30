package com.boutique.pos.config;

import com.boutique.pos.model.Role;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.repository.RoleRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.service.RoleService;
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
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        Tienda defaultTienda = ensureDefaultTienda();
        backfillTiendaId(defaultTienda);
        migrateSharedRolesToTiendas();
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

    // Un solo evento, la primera vez que hay más de una tienda: los roles ADMIN/CASHIER/SELLER
    // (y cualquier otro que se haya creado antes de que existiera el multi-tienda) eran
    // compartidos por todas las tiendas. Esto le da la propiedad de esos roles a la primera
    // tienda tal cual estaban, y le clona una copia independiente a cada tienda adicional,
    // reasignando a sus usuarios — así editar un rol en una tienda ya no afecta a las demás.
    private void migrateSharedRolesToTiendas() {
        List<Tienda> tiendas = tiendaRepository.findAllByOrderByNameAsc();
        if (tiendas.isEmpty()) return;

        List<Role> tiendaLessRoles = roleRepository.findAllByTiendaIsNull();
        tiendaLessRoles.removeIf(r -> "SUPER_ADMIN".equals(r.getName())); // ese sí es global, se queda sin tienda

        if (tiendaLessRoles.isEmpty()) return; // ya migrado, o no había nada que migrar

        Tienda owner = tiendas.get(0);
        for (Role r : tiendaLessRoles) {
            r.setTienda(owner);
        }
        roleRepository.saveAll(tiendaLessRoles);
        log.info("Roles compartidos ({}) asignados a la tienda '{}'",
                tiendaLessRoles.size(), owner.getName());

        for (Tienda t : tiendas) {
            if (t.getId().equals(owner.getId())) continue;
            for (Role template : tiendaLessRoles) {
                if (roleRepository.existsByNameAndTiendaId(template.getName(), t.getId())) continue;
                Role clone = roleService.createSeedRole(template.getName(), template.getDescription(),
                        Boolean.TRUE.equals(template.getIsSystem()), template.getSections(), t);
                int updated = jdbcTemplate.update(
                        "UPDATE users SET role_id = ? WHERE tienda_id = ? AND role_id = ?",
                        clone.getId(), t.getId(), template.getId());
                if (updated > 0) {
                    log.info("{} usuario(s) de '{}' reasignados a su propio rol '{}'",
                            updated, t.getName(), template.getName());
                }
            }
        }
    }
}
