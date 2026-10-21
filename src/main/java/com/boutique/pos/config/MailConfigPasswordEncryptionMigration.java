package com.boutique.pos.config;

import com.boutique.pos.security.CredentialEncryptionService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migración de una sola vez: cifra en su lugar cualquier {@code mail_config.smtp_password}
 * que todavía esté en texto plano de antes de este fix (hallazgo "Media" de la auditoría de
 * código). Corre DESPUÉS de {@link MailConfigDataInitializer} ({@link Order} mayor), para
 * que la fila ya exista si es un arranque desde cero.
 *
 * <p>Usa consultas nativas a propósito, no el repositorio/entidad: leer con
 * {@code MailConfigRepository} ya pasaría el valor crudo por {@code EncryptedStringConverter},
 * que intentaría DESCIFRAR un valor que todavía está en texto plano y tronaría. {@link
 * CredentialEncryptionService#looksEncrypted} hace de detector confiable (GCM rechaza
 * cualquier valor que no se haya cifrado con la misma llave) — así esta migración es
 * idempotente: en cada arranque posterior, la fila ya cifrada se detecta como tal y se
 * deja intacta, sin volver a cifrarla encima (lo que la corrompería).</p>
 */
@Component
@Order(21)
@RequiredArgsConstructor
@Slf4j
public class MailConfigPasswordEncryptionMigration implements CommandLineRunner {

    private final EntityManager entityManager;
    private final CredentialEncryptionService encryptionService;

    @Override
    @Transactional
    public void run(String... args) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT id, smtp_password FROM mail_config WHERE smtp_password IS NOT NULL")
                .getResultList();

        int migrated = 0;
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            String raw = (String) row[1];
            if (raw.isBlank() || encryptionService.looksEncrypted(raw)) continue;

            String encrypted = encryptionService.encrypt(raw);
            entityManager.createNativeQuery("UPDATE mail_config SET smtp_password = :enc WHERE id = :id")
                    .setParameter("enc", encrypted)
                    .setParameter("id", id)
                    .executeUpdate();
            migrated++;
        }
        if (migrated > 0) {
            log.info("mail_config.smtp_password: {} fila(s) migrada(s) de texto plano a cifrado", migrated);
        }
    }
}
