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
import java.util.Map;
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
        migrateLegacyUserRoles();
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

    private void migrateLegacyUserRoles() {
        List<Map<String, Object>> pending;
        try {
            pending = jdbcTemplate.queryForList(
                    "SELECT id, role FROM users WHERE role_id IS NULL AND role IS NOT NULL");
        } catch (Exception e) {
            return; // columna legacy 'role' no existe (base nueva) — nada que migrar
        }
        if (pending.isEmpty()) return;

        int migrated = 0;
        for (Map<String, Object> row : pending) {
            Long userId = ((Number) row.get("id")).longValue();
            String legacyRoleName = String.valueOf(row.get("role"));
            Role role = roleRepository.findByName(legacyRoleName).orElse(null);
            if (role == null) {
                log.warn("Usuario {} tiene rol legacy desconocido '{}', se omite", userId, legacyRoleName);
                continue;
            }
            jdbcTemplate.update("UPDATE users SET role_id = ? WHERE id = ?", role.getId(), userId);
            migrated++;
        }
        log.info("Migrados {} usuarios desde la columna legacy 'role'", migrated);
    }
}
