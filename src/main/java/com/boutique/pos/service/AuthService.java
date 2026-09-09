package com.boutique.pos.service;

import com.boutique.pos.dto.LoginRequest;
import com.boutique.pos.dto.LoginResponse;
import com.boutique.pos.exception.InvalidRefreshTokenException;
import com.boutique.pos.model.PasswordResetToken;
import com.boutique.pos.model.RefreshToken;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.PasswordResetTokenRepository;
import com.boutique.pos.repository.RefreshTokenRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.JwtTokenProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Servicio de autenticación del sistema.
 *
 * <p>Valida credenciales contra Spring Security, emite el access token JWT (vida corta,
 * 30 min) que el resto de la API espera en el header {@code Authorization} junto con un
 * refresh token opaco (8h, revocable, ver {@link #refreshAccessToken(String)} y
 * {@link #logout(String)}), y expone al usuario autenticado (usado por el endpoint "quién
 * soy" del frontend tras el login o al refrescar la sesión). También resuelve el flujo de
 * "olvidé mi contraseña" ({@link #forgotPassword(String)} / {@link #resetPassword(String,
 * String)}).</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Vigencia del link de recuperación de contraseña. Corta a propósito: reduce la ventana
    // de exposición si el correo del usuario fuera interceptado.
    private static final long RESET_TOKEN_MINUTES = 30;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationMs;

    /** Resultado interno de {@link #login}: separa el body JSON de siempre (nunca debe
     *  llevar el refresh token) del valor crudo que {@code AuthController} usa para armar
     *  la cookie httpOnly — así el refresh token nunca queda expuesto a JS del frontend. */
    @Getter @AllArgsConstructor
    public static class LoginResult {
        private final LoginResponse body;
        private final String refreshToken;
    }

    /**
     * Autentica al usuario con correo y contraseña, genera su access token JWT y un refresh
     * token nuevo (persistido, vigente {@code app.jwt.refresh-expiration}).
     *
     * <p>Delega la validación de credenciales en el {@link AuthenticationManager} de
     * Spring Security (que a su vez usa el {@code UserDetailsService} respaldado por
     * {@link UserRepository}). La respuesta incluye el rol y las secciones ({@link
     * com.boutique.pos.model.AppSection}) habilitadas para ese usuario, de modo que el
     * frontend pueda armar el menú sin pedirlas por separado, así como la tienda a la que
     * pertenece (null para usuarios de plataforma tipo SUPER_ADMIN).</p>
     *
     * @param req credenciales de acceso (correo y contraseña en texto plano)
     * @return el body de sesión de siempre más el refresh token crudo (ver {@link LoginResult})
     * @throws org.springframework.security.core.AuthenticationException si las credenciales son inválidas
     */
    @Transactional
    public LoginResult login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        User user = (User) auth.getPrincipal();
        if (user.getRole() == null) {
            // Usuario sin rol asignado (típicamente uno insertado a mano en la base sin
            // role_id) — sin esto, el .getRole().getName() de abajo truena con NPE crudo.
            throw new IllegalStateException("Tu cuenta no tiene un rol asignado, contacta a tu administrador");
        }
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = issueRefreshToken(user);
        LoginResponse body = LoginResponse.builder()
                .token(accessToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .sections(user.getRole().getSections().stream().map(Enum::name).toList())
                .tienda(user.getTienda())
                .mustChangePassword(user.getMustChangePassword())
                .build();
        return new LoginResult(body, refreshToken);
    }

    /**
     * Canjea un refresh token válido por un access token JWT nuevo, sin pedir credenciales
     * de nuevo — es lo que {@code POST /api/auth/refresh} usa para renovar la sesión de
     * forma transparente cuando el access token (vida corta, 30 min) expira.
     *
     * <p>El refresh token es reusable dentro de su ventana de vigencia (no rota en cada
     * uso): varias peticiones en paralelo con el access token vencido pueden refrescar sin
     * invalidarse entre sí.</p>
     *
     * @param rawToken valor crudo del refresh token, tal como viaja en la cookie httpOnly
     * @return un access token JWT nuevo para el usuario dueño del refresh token
     * @throws InvalidRefreshTokenException si el token no existe, está revocado o ya venció
     */
    @Transactional
    public String refreshAccessToken(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Sesión expirada, vuelve a iniciar sesión"));
        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Sesión expirada, vuelve a iniciar sesión");
        }
        return jwtTokenProvider.generateAccessToken(refreshToken.getUser());
    }

    /**
     * Revoca puntualmente el refresh token dado — usado por {@code POST /api/auth/logout}.
     * No afecta otras sesiones/dispositivos del mismo usuario (a diferencia de
     * {@link RefreshTokenRepository#revokeAllForUser}, usado al cambiar contraseña).
     *
     * @param rawToken valor crudo del refresh token a revocar; si no existe, no hace nada
     *                  (logout es idempotente, ver {@code AuthController#logout})
     */
    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByToken(rawToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    /** Crea y persiste un refresh token nuevo para el usuario dado, vigente
     *  {@code app.jwt.refresh-expiration} desde ahora, y devuelve su valor crudo. */
    private String issueRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(generateSecureToken())
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    /**
     * Recupera el usuario autenticado a partir del correo contenido en el JWT.
     *
     * @param email correo del usuario, tal como viene en el token
     * @return el usuario correspondiente
     * @throws IllegalArgumentException si no existe un usuario con ese correo
     */
    public User me(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    /**
     * Inicia el flujo de recuperación de contraseña: si el correo pertenece a un usuario
     * activo, invalida cualquier link de recuperación anterior sin usar, genera uno nuevo
     * (vigente {@value #RESET_TOKEN_MINUTES} minutos) y lo envía por correo.
     *
     * <p>Deliberadamente no distingue en su resultado si el correo existe o no (el
     * controller siempre responde el mismo mensaje genérico) — así no se puede usar este
     * endpoint para averiguar qué correos están registrados en el sistema.</p>
     *
     * @param email correo capturado por el usuario en la pantalla de "olvidé mi contraseña"
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .ifPresent(user -> {
                    passwordResetTokenRepository.invalidateAllForUser(user);
                    PasswordResetToken resetToken = PasswordResetToken.builder()
                            .token(generateSecureToken())
                            .user(user)
                            .expiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_MINUTES))
                            .build();
                    passwordResetTokenRepository.save(resetToken);
                    String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
                    emailService.sendPasswordResetEmail(user, resetLink);
                });
    }

    /**
     * Completa el flujo de recuperación: valida el token recibido por correo y, si sigue
     * vigente y no se ha usado, actualiza la contraseña del usuario y marca el token como
     * usado (para que el mismo link no pueda volver a usarse).
     *
     * <p>También revoca todos los refresh tokens activos del usuario (ver
     * {@link RefreshTokenRepository#revokeAllForUser}) — si alguien más tenía una sesión
     * abierta con la contraseña anterior, queda forzado a volver a iniciar sesión.</p>
     *
     * @param token        token recibido por correo (parámetro {@code ?token=} del link)
     * @param newPassword  nueva contraseña en texto plano, elegida por el usuario
     * @throws IllegalArgumentException si el token no existe, ya se usó, o ya venció
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("El enlace no es válido o ya fue usado"));
        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El enlace no es válido o ya venció, solicita uno nuevo");
        }
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        refreshTokenRepository.revokeAllForUser(user);
    }

    /**
     * Cambia la contraseña del usuario ya autenticado, exigiendo la actual como
     * comprobante de identidad. Apaga {@code mustChangePassword} si estaba prendido —
     * es la salida de la pantalla obligatoria que ve un usuario recién dado de alta por
     * un admin (ver {@code UserService#create}).
     *
     * <p>También revoca todos los refresh tokens activos del usuario (ver
     * {@link RefreshTokenRepository#revokeAllForUser}) — cierra cualquier otra sesión abierta
     * con la contraseña anterior.</p>
     *
     * @param actor usuario autenticado que cambia su propia contraseña
     * @param currentPassword contraseña actual, para verificar identidad
     * @param newPassword nueva contraseña elegida
     * @throws IllegalArgumentException si {@code currentPassword} no coincide con la actual
     */
    @Transactional
    public void changePassword(User actor, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, actor.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        actor.setPassword(passwordEncoder.encode(newPassword));
        actor.setMustChangePassword(false);
        userRepository.save(actor);
        refreshTokenRepository.revokeAllForUser(actor);
    }

    /** Genera un token aleatorio criptográficamente seguro, codificado en base64 URL-safe.
     *  Usado tanto por el link de recuperación de contraseña ({@link PasswordResetToken})
     *  como por el refresh token de sesión ({@link RefreshToken}) — misma necesidad de un
     *  valor opaco impredecible, distinta tabla/vigencia cada uno. */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
