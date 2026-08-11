package com.boutique.pos.dto;

import com.boutique.pos.model.Tienda;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Respuesta devuelta por {@code POST /api/auth/login} tras una autenticación exitosa.
 * Incluye el JWT que el frontend debe enviar en las siguientes peticiones, los datos
 * básicos del usuario autenticado y la información necesaria para pintar la UI según su
 * rol: las secciones ({@link com.boutique.pos.model.AppSection}) a las que tiene acceso
 * y, si aplica, la tienda a la que pertenece (para tema/branding).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long   userId;
    private String name;
    private String email;
    private String role;
    // Nombres de las AppSection a las que el usuario tiene acceso, resueltas a partir de su Role.
    private List<String> sections;
    private Tienda tienda; // null si es SUPER_ADMIN — el frontend usa tienda.primaryColor para el tema
}
