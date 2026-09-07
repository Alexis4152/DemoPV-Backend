package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ChangePasswordRequest;
import com.boutique.pos.dto.ForgotPasswordRequest;
import com.boutique.pos.dto.LoginRequest;
import com.boutique.pos.dto.LoginResponse;
import com.boutique.pos.dto.ResetPasswordRequest;
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
 * No requiere permisos de sección ni de rol específico: el endpoint de login y los de
 * recuperación de contraseña son públicos (no hay sesión todavía), y el de "usuario
 * actual" solo exige que la petición ya venga autenticada con un JWT válido.
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

    /**
     * Inicia la recuperación de contraseña: si el correo pertenece a un usuario activo, le
     * envía un link de un solo uso (vigente 30 minutos) para elegir una nueva contraseña.
     * <p>
     * Responde siempre el mismo mensaje genérico, exista o no el correo — así este endpoint
     * no se puede usar para averiguar qué correos están registrados.
     *
     * @param req correo del usuario que quiere recuperar su contraseña
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(null, "Si el correo está registrado, te enviamos un enlace para recuperar tu contraseña"));
    }

    /**
     * Completa la recuperación de contraseña a partir del token recibido por correo.
     *
     * @param req token del link y la nueva contraseña elegida por el usuario
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Contraseña actualizada, ya puedes iniciar sesión"));
    }

    /**
     * Cambia la contraseña del usuario ya autenticado (exige la actual como comprobante
     * de identidad) — a diferencia de {@link #resetPassword}, que es anónimo vía token de
     * correo. Es lo que usa la pantalla obligatoria de "cambia tu contraseña" cuando un
     * admin dio de alta al usuario con una temporal (ver {@code LoginResponse#mustChangePassword}).
     * Vive bajo {@code /api/auth/**}, que {@code SecurityConfig} marca aparte como
     * autenticado (a diferencia del resto de ese prefijo, público) para exigir JWT igual
     * que cualquier otro endpoint protegido.
     *
     * @param req contraseña actual y la nueva elegida
     * @param actor usuario autenticado, inyectado a partir del JWT
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                                              @AuthenticationPrincipal User actor) {
        authService.changePassword(actor, req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Contraseña actualizada"));
    }
}
