package com.boutique.pos.config;

import com.boutique.pos.security.CustomUserDetailsService;
import com.boutique.pos.security.JwtAuthFilter;
import com.boutique.pos.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración central de Spring Security. Define una API sin sesión (JWT stateless), sin
 * CSRF (no aplica a una API consumida por SPA con tokens), con CORS habilitado para los
 * orígenes de desarrollo del frontend, e inserta {@link JwtAuthFilter} antes del filtro
 * estándar de autenticación por usuario/contraseña para autenticar cada request por su
 * token.
 *
 * <p>{@code @EnableMethodSecurity} habilita las anotaciones {@code @PreAuthorize} usadas en
 * los controllers/services, incluyendo las que consultan {@link
 * com.boutique.pos.security.SectionAccessService} (bean {@code sectionAccess}) para el RBAC
 * configurable por sección.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Cadena de filtros de seguridad HTTP: deshabilita CSRF, activa CORS, fuerza sesiones
     * stateless, permite sin autenticación los endpoints de auth ({@code /api/auth/**}) y los
     * archivos estáticos subidos ({@code /uploads/**}), y exige autenticación para el resto.
     * El filtro JWT se registra antes de {@link UsernamePasswordAuthenticationFilter} para
     * poblar el {@code SecurityContext} a partir del token antes de que Spring intente
     * cualquier otro mecanismo de autenticación.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Excepciones DENTRO de /api/auth/**, declaradas antes que el permitAll
                        // de abajo (gana la regla más específica que aparezca primero): a
                        // diferencia de login/refresh/logout/forgot-password/reset-password
                        // (anónimos por diseño), cambiar la propia contraseña y "quién soy"
                        // sí exigen sesión — /me en particular la necesita para que un token
                        // inválido/vencido dispare el 401 real de restAuthenticationEntryPoint
                        // (y con él, el refresh transparente del frontend) en vez de responder
                        // 200 con data null vía @AuthenticationPrincipal.
                        .requestMatchers("/api/auth/change-password", "/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        // Vitrina pública de apartados (PublicController): sin login, el
                        // aislamiento entre tiendas lo da el slug de la URL, no una sesión.
                        .requestMatchers("/api/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .userDetailsService(userDetailsService)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Encoder de contraseñas usado al dar de alta usuarios y al validar el login. BCrypt
     * genera un salt distinto por hash, por lo que dos contraseñas iguales producen valores
     * almacenados distintos.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Expone el {@link AuthenticationManager} por defecto de Spring Security para que el
     * endpoint de login pueda autenticar credenciales (email/contraseña) manualmente antes de
     * emitir el JWT.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * Configura CORS para permitir que el frontend consuma la API con credenciales, incluyendo
     * el header {@code Authorization} usado para enviar el JWT. Los orígenes permitidos vienen
     * de {@code app.cors.allowed-origins} (por default, solo Vite/CRA local); en producción se
     * sobreescribe con la variable de entorno {@code APP_CORS_ALLOWED_ORIGINS} apuntando al
     * dominio real donde quede desplegado el frontend, sin tocar código.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
