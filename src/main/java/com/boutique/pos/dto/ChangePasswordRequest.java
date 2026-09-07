package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload para que un usuario YA AUTENTICADO cambie su propia contraseña
 * ({@code POST /api/auth/change-password}) — a diferencia de {@link ResetPasswordRequest}
 * (recuperación anónima vía token de correo), aquí se exige la contraseña actual como
 * comprobante de identidad, no un token. Es el flujo que usa la pantalla obligatoria de
 * "cambia tu contraseña" cuando un admin dio de alta al usuario con una temporal (ver
 * {@code User#getMustChangePassword()}), pero cualquier usuario puede usarlo también para
 * cambiarla por gusto propio.
 */
@Data
public class ChangePasswordRequest {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
    private String newPassword;
}
