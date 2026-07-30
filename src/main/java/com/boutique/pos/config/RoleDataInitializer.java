package com.boutique.pos.config;

import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.Role;
import com.boutique.pos.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        seedDefaultRoles();
        assignDefaultAdminRole();
    }

    private void seedDefaultRoles() {
        if (roleRepository.count() > 0) return;

        Set<AppSection> nonAdminSections = EnumSet.allOf(AppSection.class);
        nonAdminSections.remove(AppSection.USERS);
        nonAdminSections.remove(AppSection.ROLES);

        Role admin = Role.builder()
                .name("ADMIN")
                .description("Administrador — acceso total")
                .isSystem(true)
                .sections(EnumSet.allOf(AppSection.class))
                .build();
        Role cashier = Role.builder()
                .name("CASHIER")
                .description("Cajero")
                .sections(new HashSet<>(nonAdminSections))
                .build();
        Role seller = Role.builder()
                .name("SELLER")
                .description("Vendedor")
                .sections(new HashSet<>(nonAdminSections))
                .build();

        roleRepository.saveAll(List.of(admin, cashier, seller));
        log.info("Roles sembrados: ADMIN, CASHIER, SELLER");
    }

    // init.sql da de alta admin@boutique.com sin role_id (la tabla roles la maneja Hibernate,
    // no ese script) — este paso idempotente le asigna ADMIN si todavía no tiene rol.
    // findFirstByNameOrderById (no findByName): una vez que cada tienda tiene su propio
    // rol "ADMIN", puede haber más de uno y findByName tronaría por resultado ambiguo.
    private void assignDefaultAdminRole() {
        Role admin = roleRepository.findFirstByNameOrderById("ADMIN").orElse(null);
        if (admin == null) return;
        int updated = jdbcTemplate.update(
                "UPDATE users SET role_id = ? WHERE email = 'admin@boutique.com' AND role_id IS NULL",
                admin.getId());
        if (updated > 0) {
            log.info("Rol ADMIN asignado a admin@boutique.com");
        }
    }
}
