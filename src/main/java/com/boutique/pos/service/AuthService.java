package com.boutique.pos.service;

import com.boutique.pos.dto.LoginRequest;
import com.boutique.pos.dto.LoginResponse;
import com.boutique.pos.model.PasswordResetToken;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.PasswordResetTokenRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.JwtTokenProvider;
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
 * <p>Valida credenciales contra Spring Security, emite el JWT que el resto de la API
 * espera en el header {@code Authorization}, y expone al usuario autenticado (usado por
 * el endpoint "quién soy" del frontend tras el login o al refrescar la sesión). También
 * resuelve el flujo de "olvidé mi contraseña" ({@link #forgotPassword(String)} /
 * {@link #resetPassword(String, String)}).</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Vigencia del link de recuperación de contraseña. Corta a propósito: reduce la ventana
    // de exposición si el correo del usuario fuera interceptado.
    private static final long RESET_TOKEN_MINUTES = 30;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Autentica al usuario con correo y contraseña y genera su token JWT.
     *
     * <p>Delega la validación de credenciales en el {@link AuthenticationManager} de
     * Spring Security (que a su vez usa el {@code UserDetailsService} respaldado por
     * {@link UserRepository}). La respuesta incluye el rol y las secciones ({@link
     * com.boutique.pos.model.AppSection}) habilitadas para ese usuario, de modo que el
     * frontend pueda armar el menú sin pedirlas por separado, así como la tienda a la que
     * pertenece (null para usuarios de plataforma tipo SUPER_ADMIN).</p>
     *
     * @param req credenciales de acceso (correo y contraseña en texto plano)
     * @return datos de sesión: token JWT, datos básicos del usuario, rol y secciones habilitadas
     * @throws org.springframework.security.core.AuthenticationException si las credenciales son inválidas
     */
    public LoginResponse login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        User user = (User) auth.getPrincipal();
        String token = jwtTokenProvider.generateToken(user);
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .sections(user.getRole().getSections().stream().map(Enum::name).toList())
                .tienda(user.getTienda())
                .mustChangePassword(user.getMustChangePassword())
                .build();
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
                            .token(generateResetToken())
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
    }

    /**
     * Cambia la contraseña del usuario ya autenticado, exigiendo la actual como
     * comprobante de identidad. Apaga {@code mustChangePassword} si estaba prendido —
     * es la salida de la pantalla obligatoria que ve un usuario recién dado de alta por
     * un admin (ver {@code UserService#create}).
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
    }

    /** Genera un token aleatorio criptográficamente seguro, codificado en base64 URL-safe. */
    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
