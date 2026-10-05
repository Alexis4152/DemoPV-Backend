package com.boutique.pos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Credenciales de acceso enviadas a {@code POST /api/auth/login}. Es el único DTO de
 * entrada del flujo de autenticación; si las credenciales son válidas, el servicio
 * responde con un {@link LoginResponse} que incluye el JWT.
 */
@Data
public class LoginRequest {
    // Mensaje propio en vez del default de Jakarta ("debe ser una dirección de correo
    // electrónico con formato correcto") — mismo texto que valida el frontend (Login.jsx).
    @Email(message = "El correo no tiene un formato válido") @NotBlank
    private String email;
    @NotBlank
    private String password;
}
