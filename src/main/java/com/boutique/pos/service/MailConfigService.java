package com.boutique.pos.service;

import com.boutique.pos.dto.MailConfigRequest;
import com.boutique.pos.dto.MailConfigResponse;
import com.boutique.pos.model.MailConfig;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.MailConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administra la fila única de {@link MailConfig} — la configuración SMTP global con la que
 * {@link EmailService} manda todos los correos del sistema. Exclusivo de SUPER_ADMIN, ver
 * {@code AdminMailConfigController}.
 */
@Service
@RequiredArgsConstructor
public class MailConfigService {

    private final MailConfigRepository mailConfigRepository;

    /**
     * Obtiene la fila de configuración vigente, para que {@link EmailService} arme el
     * remitente SMTP con ella en cada envío.
     *
     * @throws IllegalStateException si la tabla está vacía — no debería pasar en un
     *         ambiente normal, {@code MailConfigDataInitializer} la siembra al arrancar.
     */
    public MailConfig getEntity() {
        return mailConfigRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("La configuración de correo no está inicializada"));
    }

    /** Versión para el panel de administración: nunca expone la contraseña real. */
    public MailConfigResponse get() {
        return toResponse(getEntity());
    }

    /**
     * Actualiza la configuración SMTP global.
     *
     * @param request nuevos valores; {@code smtpPassword} vacío/null conserva la guardada
     * @param actor quien hace el cambio, queda registrado como {@code updatedBy}
     */
    @Transactional
    public MailConfigResponse update(MailConfigRequest request, User actor) {
        MailConfig config = getEntity();
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setSmtpHost(request.getSmtpHost());
        config.setSmtpPort(request.getSmtpPort());
        config.setSmtpUsername(request.getSmtpUsername());
        if (request.getSmtpPassword() != null && !request.getSmtpPassword().isBlank()) {
            config.setSmtpPassword(request.getSmtpPassword());
        }
        config.setUpdatedBy(actor);
        return toResponse(mailConfigRepository.save(config));
    }

    private MailConfigResponse toResponse(MailConfig config) {
        return MailConfigResponse.builder()
                .enabled(Boolean.TRUE.equals(config.getEnabled()))
                .smtpHost(config.getSmtpHost())
                .smtpPort(config.getSmtpPort())
                .smtpUsername(config.getSmtpUsername())
                .passwordConfigured(config.getSmtpPassword() != null && !config.getSmtpPassword().isBlank())
                .build();
    }
}
