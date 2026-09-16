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
    // 72 como tope porque BCrypt trunca en silencio cualquier byte de más allá del 72
    // (mismo motivo que UserRequest.password).
    @NotBlank
    @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
    private String newPassword;
}
