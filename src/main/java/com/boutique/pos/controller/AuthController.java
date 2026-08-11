package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.LoginRequest;
import com.boutique.pos.dto.LoginResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de autenticación, expuesto bajo {@code /api/auth}.
 * <p>
 * No requiere permisos de sección ni de rol específico: el endpoint de login es
 * público (punto de entrada al sistema) y el de "usuario actual" solo exige que
 * la petición ya venga autenticada con un JWT válido.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Autentica a un usuario con email y contraseña y emite el token JWT que debe
     * usarse en el resto de las llamadas a la API (header {@code Authorization}).
     *
     * @param req credenciales de acceso (email y contraseña)
     * @return datos de sesión (token y datos básicos del usuario) envueltos en {@link ApiResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse res = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok(res, "Login exitoso"));
    }

    /**
     * Devuelve la información del usuario autenticado en la sesión actual, tal
     * como fue resuelto a partir del JWT de la petición.
     *
     * @param user usuario autenticado, inyectado por Spring Security a partir del token
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(user, "Usuario actual"));
    }
}
