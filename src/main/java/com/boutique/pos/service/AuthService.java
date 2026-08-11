package com.boutique.pos.service;

import com.boutique.pos.dto.LoginRequest;
import com.boutique.pos.dto.LoginResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación del sistema.
 *
 * <p>Valida credenciales contra Spring Security, emite el JWT que el resto de la API
 * espera en el header {@code Authorization}, y expone al usuario autenticado (usado por
 * el endpoint "quién soy" del frontend tras el login o al refrescar la sesión).</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

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
}
