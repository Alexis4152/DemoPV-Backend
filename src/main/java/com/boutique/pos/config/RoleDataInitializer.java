package com.boutique.pos.config;

import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.Role;
import com.boutique.pos.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link CommandLineRunner} que siembra los roles por defecto del sistema (ADMIN, CASHIER,
 * SELLER) la primera vez que arranca la aplicación con una base de datos sin roles, y deja
 * asignado el rol ADMIN a la cuenta administradora inicial ({@code admin@boutique.com}).
 *
 * <p>{@code @Order(1)} garantiza que corra antes que {@link TenantDataInitializer}
 * ({@code @Order(10)}), ya que este último migra roles compartidos entre tiendas y necesita
 * que los roles base ya existan. Sin esta anotación, un {@link CommandLineRunner} sin orden
 * explícito se trata como {@code Ordered.LOWEST_PRECEDENCE} y correría después, al revés de
 * lo requerido.</p>
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RoleDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Orquesta el sembrado inicial: primero crea los roles base si la tabla está vacía,
     * luego asegura que exista el rol SUPER_ADMIN (chequeo aparte, no gira sobre "la tabla
     * está vacía" porque para cuando se agregó este rol la tabla ya casi nunca lo está), y
     * por último asegura que la cuenta admin por defecto tenga un rol asignado.
     */
    @Override
    @Transactional
    public void run(String... args) {
        seedDefaultRoles();
        seedSuperAdminRole();
        assignDefaultAdminRole();
    }

    /**
     * Crea los roles ADMIN (acceso total, marcado {@code isSystem}), CASHIER y SELLER (todas
     * las secciones excepto USERS y ROLES) si todavía no existe ningún rol en la base de
     * datos. No hace nada si ya hay al menos un rol sembrado, para no duplicar en arranques
     * posteriores.
     */
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

    // Chequeo aparte de seedDefaultRoles() (que solo corre con la tabla TOTALMENTE vacía):
    // este rol se agregó cuando ya casi ninguna base de datos real estaba vacía, así que
    // necesita su propio guard idempotente ("¿ya existe uno llamado SUPER_ADMIN?") en vez
    // de colgarse del mismo "count() > 0" de los otros tres.
    /**
     * Crea el rol SUPER_ADMIN (usuario de plataforma, sin tienda, con TODAS las secciones
     * habilitadas — ve y administra cualquier tienda, una a la vez, vía {@code
     * SelectTienda.jsx} en el frontend) si todavía no existe ninguno con ese nombre. No
     * crea ningún usuario con este rol — eso sigue siendo un paso manual (dar de alta un
     * usuario y asignarle este rol desde la pantalla de Usuarios, o directo en la base de
     * datos), a propósito: quién tiene acceso de plataforma completo es una decisión que
     * no debe tomar un script de arranque.
     */
    private void seedSuperAdminRole() {
        if (roleRepository.findFirstByNameOrderById("SUPER_ADMIN").isPresent()) return;
        Role superAdmin = Role.builder()
                .name("SUPER_ADMIN")
                .description("Super administrador — plataforma completa, todas las tiendas")
                .isSystem(true)
                .tienda(null)
                .sections(EnumSet.allOf(AppSection.class))
                .build();
        roleRepository.save(superAdmin);
        log.info("Rol sembrado: SUPER_ADMIN");
    }

    /**
     * Asigna el rol ADMIN a la cuenta administradora sembrada por {@code init.sql} en caso de
     * que todavía no tenga {@code role_id}. Es idempotente: solo actualiza filas con
     * {@code role_id IS NULL}, así que en arranques posteriores no hace nada.
     */
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
