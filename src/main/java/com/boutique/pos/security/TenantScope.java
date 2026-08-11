package com.boutique.pos.security;

import com.boutique.pos.model.User;
import org.springframework.stereotype.Component;

/**
 * Helper central de aislamiento multi-tienda ("Tienda"), usado por prácticamente todos los
 * services de negocio para decidir qué datos puede ver o modificar el usuario que hace la
 * petición ({@code actor}). Encapsula en un solo lugar la regla de que el rol SUPER_ADMIN es
 * la única excepción al aislamiento por tienda: es un usuario de plataforma (sin tienda
 * asignada) que ve y administra todas las tiendas.
 */
@Component
public class TenantScope {

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si su rol es {@code SUPER_ADMIN} (usuario de plataforma, sin
     *         tienda propia, con visibilidad total)
     */
    public boolean isSuperAdmin(User actor) {
        return "SUPER_ADMIN".equals(actor.getRole().getName());
    }

    /**
     * Calcula el id de tienda por el que se debe filtrar una consulta para este actor.
     *
     * @param actor usuario que realiza la operación
     * @return {@code null} si es SUPER_ADMIN (sin filtro, ve todas las tiendas) o el id de su
     *         propia tienda en cualquier otro caso
     */
    // null = sin filtro (SUPER_ADMIN ve todas las tiendas)
    public Long scopeId(User actor) {
        if (isSuperAdmin(actor)) return null;
        return actor.getTienda() != null ? actor.getTienda().getId() : null;
    }

    /**
     * Determina si el actor tiene permiso de escritura sobre una tienda específica (por
     * ejemplo, para editar su personalización: color de marca, logo, datos fiscales).
     *
     * @param actor    usuario que realiza la operación
     * @param tiendaId id de la tienda que se quiere administrar
     * @return {@code true} si es SUPER_ADMIN (puede administrar cualquier tienda), o si el
     *         actor pertenece exactamente a esa tienda; {@code false} en cualquier otro caso
     */
    // SUPER_ADMIN puede administrar cualquier tienda; un ADMIN normal solo la suya propia
    // (color de marca, logo, datos fiscales, etc.)
    public boolean canManageTienda(User actor, Long tiendaId) {
        if (isSuperAdmin(actor)) return true;
        return actor.getTienda() != null && actor.getTienda().getId().equals(tiendaId);
    }
}
