package com.boutique.pos.security;

import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.Role;
import com.boutique.pos.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Servicio de autorización por sección (RBAC configurable) expuesto como bean {@code
 * "sectionAccess"} para usarse en expresiones {@code @PreAuthorize("@sectionAccess.check('X')")}
 * en controllers y services. A diferencia de un RBAC fijo en código, las secciones habilitadas
 * ({@link AppSection}: DASHBOARD, POS, INVENTORY, SALES, CASH_CUTS, REPORTS, USERS, ROLES) se
 * definen por {@link com.boutique.pos.model.Role} y las administra el admin de cada tienda.
 */
@Component("sectionAccess")
public class SectionAccessService {

    /**
     * Verifica que el usuario autenticado tenga habilitada la sección dada en su rol.
     *
     * @param section nombre de un {@link AppSection} (ej. {@code "SALES"})
     * @return {@code true} si el usuario tiene sesión activa, tiene un rol asignado y ese rol
     *         incluye la sección solicitada
     */
    public boolean check(String section) {
        return checkAny(section);
    }

    /**
     * Igual que {@link #check(String)} pero satisfecho si el rol del usuario tiene habilitada
     * al menos una de las secciones dadas.
     *
     * @param sections una o más secciones (nombres de {@link AppSection}); basta con que una
     *                 coincida
     * @return {@code true} si el usuario tiene rol y ese rol incluye alguna de las secciones
     */
    public boolean checkAny(String... sections) {
        User user = currentUser();
        if (user == null || user.getRole() == null) return false;
        for (String code : sections) {
            if (user.getRole().getSections().contains(AppSection.valueOf(code))) {
                return true;
            }
        }
        return false;
    }

    // Mismos tres roles "de gestión" que AuthService#SESSION_RESTRICTION_EXEMPT_ROLES y
    // UserService#ROLE_RANK — nunca quedan sujetos a actionGrants, siempre CRUD completo
    // en cualquier sección que vean. Un rol personalizado (o CASHIER) SÍ queda sujeto.
    private static final Set<String> MANAGEMENT_ROLES = Set.of("SUPER_ADMIN", "SUPERVISOR", "ADMIN");

    /**
     * Verifica que el usuario autenticado pueda realizar una ACCIÓN de mutación concreta
     * (crear/editar/eliminar) dentro de una sección a la que ya tiene acceso — capa más
     * fina que {@link #check(String)}, que solo valida si ve el módulo en absoluto.
     *
     * <p>Los roles "de gestión" ({@link #MANAGEMENT_ROLES}) siempre pasan, sin importar
     * {@link Role#getActionGrants()}. Para cualquier otro rol, la acción debe estar
     * explícitamente otorgada (ver {@link Role#getActionGrants()}) — ausente significa sin
     * permiso, nunca al revés.</p>
     *
     * @param section nombre de un {@link AppSection} (ej. {@code "INVENTORY"})
     * @param action  nombre de la acción (ej. {@code "CREATE"}, {@code "EDIT"}, {@code "DELETE"})
     * @return {@code true} si el usuario tiene sesión activa, rol, acceso a esa sección, y
     *         (siendo un rol de gestión, o teniendo esa acción explícitamente otorgada)
     */
    public boolean checkAction(String section, String action) {
        User user = currentUser();
        if (user == null || user.getRole() == null) return false;
        Role role = user.getRole();
        if (!role.getSections().contains(AppSection.valueOf(section))) return false;
        if (MANAGEMENT_ROLES.contains(role.getName())) return true;
        return role.getActionGrants().contains(section + ":" + action);
    }

    /**
     * Obtiene al usuario autenticado en el contexto de seguridad actual.
     *
     * @return el {@link User} principal de la autenticación actual, o {@code null} si no hay
     *         autenticación o el principal no es un {@code User} (por ejemplo, request anónimo)
     */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) return null;
        return (User) auth.getPrincipal();
    }
}
