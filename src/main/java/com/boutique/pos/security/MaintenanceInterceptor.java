package com.boutique.pos.security;

import com.boutique.pos.model.User;
import com.boutique.pos.service.MaintenanceWindowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Durante una ventana de mantenimiento activa, corta toda petición de un usuario con sesión
 * que no sea SUPER_ADMIN — incluidas las sesiones que ya estaban abiertas cuando empezó la
 * ventana (un cajero a media venta ve la pantalla de mantenimiento en su siguiente acción).
 * Responde 503 vía {@link com.boutique.pos.exception.MaintenanceModeException}.
 *
 * <p>No aplica a lo público ({@code /api/public/**}: el Login lee de ahí la ventana) ni a
 * {@code /api/auth/**}: login y refresh hacen su propia validación en AuthService (ahí ya se
 * sabe quién es), y logout debe funcionar siempre.</p>
 */
@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {

    private final MaintenanceWindowService maintenanceWindowService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/public/") || path.startsWith("/api/auth/")) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            maintenanceWindowService.assertCanOperate(user);
        }
        return true;
    }
}
