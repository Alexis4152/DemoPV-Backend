package com.boutique.pos.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Credenciales de acceso enviadas a {@code POST /api/auth/login}. Es el único DTO de
 * entrada del flujo de autenticación; si las credenciales son válidas, el servicio
 * responde con un {@link LoginResponse} que incluye el JWT.
 */
@Data
public class LoginRequest {
    // Correo O teléfono (10 dígitos) — el nombre del campo se queda "email" para no
    // cambiar el contrato con el frontend. Sin @Email: un teléfono no tiene formato de
    // correo; AuthService#resolveLoginEmail decide cuál es.
    @NotBlank(message = "Escribe tu correo o teléfono")
    private String email;
    @NotBlank
    private String password;
}
