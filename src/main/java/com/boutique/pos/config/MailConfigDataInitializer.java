package com.boutique.pos.config;

import com.boutique.pos.model.MailConfig;
import com.boutique.pos.repository.MailConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra la fila única de {@link MailConfig} la primera vez que arranca la aplicación con
 * la tabla {@code mail_config} vacía.
 *
 * <p>La sembrada queda {@code enabled=false} y SIN credenciales — un host/puerto de
 * ejemplo (Gmail) nada más como punto de partida. Ya no toma ninguna contraseña real de
 * {@code application.properties}/variables de entorno (antes sí, y esa contraseña real
 * terminaba en texto plano tanto en el archivo versionado en git como en esta misma tabla
 * — hallazgos "Alta"/"Media" de la auditoría de código). Un SUPER_ADMIN captura la cuenta
 * real desde {@code /api/admin/mail-config} — mientras tanto, el envío de correo
 * simplemente se omite (ver {@code EmailService}, que ya revisa {@code enabled} antes de
 * intentar mandar cualquier cosa), sin bloquear el resto de la app.</p>
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class MailConfigDataInitializer implements CommandLineRunner {

    private final MailConfigRepository mailConfigRepository;

    @Override
    public void run(String... args) {
        if (mailConfigRepository.count() > 0) return;
        mailConfigRepository.save(MailConfig.builder()
                .enabled(false)
                .smtpHost("smtp.gmail.com")
                .smtpPort(587)
                .build());
        log.info("Configuración de correo inicial sembrada en mail_config (deshabilitada, sin credenciales — configúrala en /api/admin/mail-config)");
    }
}
