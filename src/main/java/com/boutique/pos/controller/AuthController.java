package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ChangePasswordRequest;
import com.boutique.pos.dto.ForgotPasswordRequest;
import com.boutique.pos.dto.LoginRequest;
import com.boutique.pos.dto.LoginResponse;
import com.boutique.pos.dto.RefreshResponse;
import com.boutique.pos.dto.ResetPasswordRequest;
import com.boutique.pos.exception.InvalidRefreshTokenException;
import com.boutique.pos.model.User;
import com.boutique.pos.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de autenticación, expuesto bajo {@code /api/auth}.
 * <p>
 * No requiere permisos de sección ni de rol específico: el endpoint de login y los de
 * recuperación de contraseña son públicos (no hay sesión todavía), y el de "usuario
 * actual" solo exige que la petición ya venga autenticada con un JWT válido.
 * <p>
 * El refresh token de sesión viaja SIEMPRE como cookie httpOnly ({@code pos_refresh_token},
 * scope {@code /api/auth}) — nunca en el body JSON, para que JS del frontend no pueda leerlo
 * (mitiga robo vía XSS). {@code login}/{@code refresh}/{@code logout} son los únicos puntos
 * que la tocan.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "pos_refresh_token";

    private final AuthService authService;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.cookie.samesite}")
    private String cookieSameSite;

    /**
     * Autentica a un usuario con email y contraseña, emite el access token JWT que debe
     * usarse en el resto de las llamadas a la API (header {@code Authorization}) y adjunta
     * el refresh token nuevo como cookie httpOnly.
     *
     * @param req credenciales de acceso (email y contraseña)
     * @param response respuesta HTTP, usada para adjuntar la cookie del refresh token
     * @return datos de sesión (token y datos básicos del usuario) envueltos en {@link ApiResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req,
                                                              HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(req);
        addRefreshCookie(response, result.getRefreshToken(), refreshExpirationMs / 1000);
        return ResponseEntity.ok(ApiResponse.ok(result.getBody(), "Login exitoso"));
    }

    /**
     * Canjea el refresh token de la cookie httpOnly por un access token JWT nuevo, sin pedir
     * credenciales de nuevo — usado por el interceptor de axios del frontend cuando una
     * petición falla con 401 por access token vencido.
     *
     * @param refreshToken valor de la cookie {@code pos_refresh_token}; ausente si nunca hubo
     *                      login o la cookie ya expiró/fue borrada
     * @return el access token JWT nuevo
     * @throws InvalidRefreshTokenException (401) si no hay cookie, o el token es inválido/venció
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException("Sesión expirada, vuelve a iniciar sesión");
        }
        String accessToken = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(RefreshResponse.builder().token(accessToken).build(), "Token renovado"));
    }

    /**
     * Cierra la sesión: revoca el refresh token actual del lado servidor y limpia la cookie.
     * Idempotente — responde 200 incluso si ya no había cookie que revocar.
     *
     * @param refreshToken valor de la cookie {@code pos_refresh_token}, si existe
     * @param response respuesta HTTP, usada para borrar la cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        addRefreshCookie(response, "", 0);
        return ResponseEntity.ok(ApiResponse.ok(null, "Sesión cerrada"));
    }

    /** Adjunta (o borra, con {@code maxAgeSeconds=0}) la cookie httpOnly del refresh token. */
    private void addRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
