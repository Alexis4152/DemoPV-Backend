package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload para actualizar la configuración SMTP global ({@code PUT /api/admin/mail-config},
 * exclusivo de SUPER_ADMIN).
 * <p>
 * {@code smtpPassword} es de solo escritura: si viene vacío o null se conserva la
 * contraseña ya guardada, nunca se sobreescribe con "" (ver {@code MailConfigService}) — así
 * el formulario nunca necesita mostrar/reenviar la contraseña real para dejarla como está.
 */
@Data
public class MailConfigRequest {

    @NotNull(message = "Indica si el envío de correo está habilitado")
    private Boolean enabled;

    @NotBlank(message = "El host SMTP es obligatorio")
    private String smtpHost;

    @NotNull(message = "El puerto SMTP es obligatorio")
    private Integer smtpPort;

    @NotBlank(message = "El usuario/cuenta SMTP es obligatorio")
    private String smtpUsername;

    private String smtpPassword;
}
