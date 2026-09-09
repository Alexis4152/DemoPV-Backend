package com.boutique.pos.config;

import com.boutique.pos.model.MailConfig;
import com.boutique.pos.repository.MailConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra la fila única de {@link MailConfig} la primera vez que arranca la aplicación con
 * la tabla {@code mail_config} vacía, tomando los valores que hasta ahora vivían fijos en
 * application.properties/variables de entorno (una sola cuenta Gmail para todo el sistema).
 *
 * <p>A partir de ese primer arranque, {@code spring.mail.*} deja de leerse en tiempo de
 * ejecución (solo lo usa Spring para el bean autoconfigurado que ya no se usa) — un
 * SUPER_ADMIN administra host/usuario/contraseña desde {@code /api/admin/mail-config} (ver
 * {@code MailConfigService}), sin necesitar un redeploy para rotar la contraseña o cambiar
 * de cuenta. Arranques posteriores no tocan la fila ya existente, aunque estos valores por
 * default cambien.</p>
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class MailConfigDataInitializer implements CommandLineRunner {

    private final MailConfigRepository mailConfigRepository;

    @Value("${spring.mail.host}")
    private String defaultHost;

    @Value("${spring.mail.port}")
    private Integer defaultPort;

    @Value("${spring.mail.username}")
    private String defaultUsername;

    @Value("${spring.mail.password}")
    private String defaultPassword;

    @Override
    public void run(String... args) {
        if (mailConfigRepository.count() > 0) return;
        mailConfigRepository.save(MailConfig.builder()
                .enabled(true)
                .smtpHost(defaultHost)
                .smtpPort(defaultPort)
                .smtpUsername(defaultUsername)
                .smtpPassword(defaultPassword)
                .build());
        log.info("Configuración de correo inicial sembrada en mail_config (host={}, usuario={})", defaultHost, defaultUsername);
    }
}
