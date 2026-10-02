package com.boutique.pos.security;

import com.boutique.pos.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.Map;

/**
 * Límite de peticiones por IP en endpoints públicos sin autenticación (hallazgo "Alto" de la
 * auditoría de código: "Sin límite de intentos (rate limiting) en login ni en recuperación
 * de contraseña — expuesto a fuerza bruta de credenciales y a spam del buzón SMTP", más el
 * hallazgo relacionado sobre el endpoint público de apartados sin límite de tasa). Mismo
 * mecanismo ya usado en el proyecto de Citas: un token bucket en memoria por IP (ver
 * {@link RateLimiterService}), sin tocar la autenticación real (esos endpoints ya eran
 * públicos por diseño — este interceptor no los protege con sesión, solo limita cuántas
 * veces se puede golpear cada uno desde la misma IP).
 * <p>
 * Las reglas se buscan por el PATRÓN de la ruta (ej. {@code /api/public/tiendas/{slug}/apartados},
 * vía {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}, disponible en {@code preHandle}
 * porque el handler mapping ya corrió) y no por la URI concreta — así una sola regla cubre el
 * endpoint sin importar el slug de cada tienda. La clave del bucket en sí SÍ usa la URI
 * concreta (con el slug real): así el abuso contra la vitrina de una tienda no consume el
 * límite de otra tienda distinta.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private record Rule(int capacity, Duration window) {}

    private static final Map<String, Rule> RULES = Map.of(
            "POST:/api/auth/login", new Rule(10, Duration.ofMinutes(1)),
            "POST:/api/auth/forgot-password", new Rule(3, Duration.ofMinutes(15)),
            // Manda un correo de aviso al admin de la tienda por cada apartado solicitado:
            // límite bajo para que nadie lo use para inundarle el correo, o para saturar el
            // endpoint con solicitudes falsas (segundo hallazgo de la auditoría).
            "POST:/api/public/tiendas/{slug}/apartados", new Rule(5, Duration.ofMinutes(1))
    );

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        Rule rule = RULES.get(request.getMethod() + ":" + (pattern != null ? pattern : request.getRequestURI()));
        if (rule == null) {
            return true;
        }
        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        if (rateLimiterService.tryConsume(key, rule.capacity(), rule.window())) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error("Demasiadas solicitudes. Intenta de nuevo más tarde.")));
        return false;
    }
}
