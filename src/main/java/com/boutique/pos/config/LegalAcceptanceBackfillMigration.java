package com.boutique.pos.config;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Marca como ya aceptados los Términos y condiciones/Aviso de privacidad de los usuarios
 * que ya existían antes de que {@code User#termsAcceptedAt}/{@code privacyNoticeAcceptedAt}
 * existieran — su "primer login" ya pasó hace tiempo, así que no deben ver la pantalla de
 * aceptación de la nada en su siguiente inicio de sesión. Solo toca filas todavía en null,
 * así que es idempotente y nunca pisa una aceptación real ya registrada.
 */
@Component
@Order(32)
@RequiredArgsConstructor
@Slf4j
public class LegalAcceptanceBackfillMigration implements CommandLineRunner {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        int updated = entityManager.createNativeQuery(
                "UPDATE users SET " +
                "terms_accepted_at = COALESCE(terms_accepted_at, created_at, now()), " +
                "privacy_notice_accepted_at = COALESCE(privacy_notice_accepted_at, created_at, now()) " +
                "WHERE terms_accepted_at IS NULL OR privacy_notice_accepted_at IS NULL").executeUpdate();
        if (updated > 0) {
            log.info("Aceptación de términos/privacidad retroactiva aplicada a {} usuario(s) existente(s)", updated);
        }
    }
}
