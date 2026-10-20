package com.boutique.pos.config;

import com.boutique.pos.model.AppSection;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Mantiene al día la restricción {@code role_sections_section_check} con los valores de
 * {@link AppSection} (mismo caso que {@link CashMovementTypeConstraintMigration}:
 * {@code ddl-auto=update} no la actualiza cuando el enum crece). Corre antes de sembrar
 * roles, que pueden recibir todas las secciones.
 *
 * <p>Además, la primera vez que aparece una sección nueva de materia prima
 * ({@code RAW_MATERIALS}), se la da a los roles de gestión (ADMIN, SUPERVISOR,
 * SUPER_ADMIN), que tienen todas las demás. Solo cuando ningún rol la tiene todavía, para
 * no volver a ponerla si después alguien la quita.</p>
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class RoleSectionConstraintMigration implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        String values = Arrays.stream(AppSection.values())
                .map(s -> "'" + s.name() + "'")
                .collect(Collectors.joining(", "));
        entityManager.createNativeQuery("ALTER TABLE role_sections DROP CONSTRAINT IF EXISTS role_sections_section_check").executeUpdate();
        entityManager.createNativeQuery("ALTER TABLE role_sections ADD CONSTRAINT role_sections_section_check CHECK (section IN (" + values + "))").executeUpdate();
        log.info("Restricción role_sections_section_check actualizada: {}", values);

        Number withRaw = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM role_sections WHERE section = 'RAW_MATERIALS'").getSingleResult();
        if (withRaw.longValue() == 0) {
            int added = entityManager.createNativeQuery(
                    "INSERT INTO role_sections (role_id, section) " +
                    "SELECT r.id, 'RAW_MATERIALS' FROM roles r WHERE r.name IN ('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')").executeUpdate();
            log.info("Sección RAW_MATERIALS agregada a {} rol(es) de gestión", added);
        }
    }
}
