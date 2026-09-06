package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload de {@code POST /api/auth/reset-password}: el token recibido por correo (ver
 * {@code AuthService#forgotPassword}) y la nueva contraseña elegida por el usuario.
 */
@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;
    @NotBlank
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String newPassword;
}
