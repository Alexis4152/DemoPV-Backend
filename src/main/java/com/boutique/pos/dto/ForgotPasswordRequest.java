package com.boutique.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload de {@code POST /api/auth/forgot-password}: solo el correo del usuario que
 * quiere recuperar su contraseña.
 */
@Data
public class ForgotPasswordRequest {
    @Email @NotBlank
    private String email;
}
