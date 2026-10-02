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
 * {@link CommandLineRunner} que siembra los roles por defecto del sistema (ADMIN, CASHIER)
 * la primera vez que arranca la aplicación con una base de datos sin roles, y deja
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
        seedSupervisorRole();
        assignDefaultAdminRole();
        protectExistingCashierRoles();
    }

    /**
     * Crea los roles ADMIN y CASHIER (ambos {@code isSystem}, protegidos de renombrar o
     * eliminar) si todavía no existe ningún rol en la base de datos. ADMIN tiene todas las
     * secciones; CASHIER todas excepto USERS y ROLES, sin ninguna acción de mutación
     * otorgada por defecto (ver {@code Role#actionGrants}). No hace nada si ya hay al
     * menos un rol sembrado, para no duplicar en arranques posteriores.
     *
     * <p>Ya NO siembra SELLER — ver {@code RoleService#seedDefaultRolesForTienda} para el
     * porqué (son 4 roles fijos ahora: SUPER_ADMIN, SUPERVISOR, ADMIN, CASHIER).</p>
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
                .isSystem(true)
                .sections(new HashSet<>(nonAdminSections))
                .build();

        roleRepository.saveAll(List.of(admin, cashier));
        log.info("Roles sembrados: ADMIN, CASHIER");
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
        Role existing = roleRepository.findFirstByNameAndTiendaIsNullOrderById("SUPER_ADMIN").orElse(null);
        if (existing != null) {
            backfillSectionsIfEmpty(existing);
            return;
        }
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

    // Detectado en producción: una fila SUPER_ADMIN de antes de que este rol tuviera
    // secciones (o sembrada por algún otro camino) se quedaba con `role_sections` vacío
    // para siempre — el guard idempotente de arriba nunca la volvía a tocar una vez que
    // existía, así que ese usuario solo veía el submenú "Configuración" (lo único que no
    // depende de `hasSection` en el frontend) sin importar cuántas veces se reiniciara el
    // backend. Se corrige solo en cada arranque en vez de requerir una migración manual.
    /**
     * Si el rol ya existe pero se quedó sin ninguna sección asignada (dato corrupto/legado,
     * nunca un estado válido para SUPER_ADMIN o SUPERVISOR), lo rellena con todas.
     */
    private void backfillSectionsIfEmpty(Role role) {
        if (!role.getSections().isEmpty()) return;
        role.setSections(EnumSet.allOf(AppSection.class));
        roleRepository.save(role);
        log.warn("Rol {} tenía 0 secciones asignadas — se rellenó con todas", role.getName());
    }

    // Mismo patrón idempotente que seedSuperAdminRole(): chequeo propio por nombre, no por
    // count() de la tabla completa.
    /**
     * Crea el rol SUPERVISOR ("Supervisor de tiendas": usuario de plataforma, sin tienda
     * propia, con TODAS las secciones habilitadas — ve y administra el SUBCONJUNTO de
     * tiendas que tenga asignadas, vía {@link com.boutique.pos.model.Tienda#getSupervisor()})
     * si todavía no existe ninguno con ese nombre. Igual que con SUPER_ADMIN, no crea ningún
     * usuario con este rol ni le asigna tiendas — eso lo decide el SUPER_ADMIN (o el propio
     * Supervisor, al dar de alta una tienda nueva) desde la aplicación.
     */
    private void seedSupervisorRole() {
        Role existing = roleRepository.findFirstByNameAndTiendaIsNullOrderById("SUPERVISOR").orElse(null);
        if (existing != null) {
            backfillSectionsIfEmpty(existing);
            return;
        }
        Role supervisor = Role.builder()
                .name("SUPERVISOR")
                .description("Supervisor de tiendas — administra el grupo de tiendas que tenga asignado")
                .isSystem(true)
                .tienda(null)
                .sections(EnumSet.allOf(AppSection.class))
                .build();
        roleRepository.save(supervisor);
        log.info("Rol sembrado: SUPERVISOR");
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

    // CASHIER pasó a ser uno de los 4 roles fijos del sistema (protegido de renombrar/
    // eliminar) junto con ADMIN/SUPERVISOR/SUPER_ADMIN, pero las filas "CASHIER" creadas
    // ANTES de este cambio (una por cada tienda ya existente) se quedaron con
    // isSystem=false, porque el sembrado de arriba (seedDefaultRoles/
    // RoleService#seedDefaultRolesForTienda) solo corre una vez por tienda. Este paso se
    // corre en CADA arranque y solo toca las filas que todavía lo necesiten — así cualquier
    // CASHIER ya existente queda protegido sin esperar a que alguien lo vuelva a guardar
    // manualmente desde "Roles y Permisos".
    /**
     * Marca {@code isSystem=true} en cualquier rol llamado CASHIER que se haya quedado
     * como {@code isSystem=false} (sembrado antes de que pasara a ser uno de los 4 roles
     * fijos del sistema).
     */
    private void protectExistingCashierRoles() {
        int updated = jdbcTemplate.update(
                "UPDATE roles SET is_system = true WHERE name = 'CASHIER' AND is_system = false");
        if (updated > 0) {
            log.info("{} rol(es) CASHIER marcado(s) como fijo(s) (isSystem=true)", updated);
        }
    }
}
