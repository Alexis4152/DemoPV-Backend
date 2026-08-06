package com.boutique.pos.security;

import com.boutique.pos.model.User;
import org.springframework.stereotype.Component;

@Component
public class TenantScope {

    public boolean isSuperAdmin(User actor) {
        return "SUPER_ADMIN".equals(actor.getRole().getName());
    }

    // null = sin filtro (SUPER_ADMIN ve todas las tiendas)
    public Long scopeId(User actor) {
        if (isSuperAdmin(actor)) return null;
        return actor.getTienda() != null ? actor.getTienda().getId() : null;
    }

    // SUPER_ADMIN puede administrar cualquier tienda; un ADMIN normal solo la suya propia
    // (color de marca, logo, datos fiscales, etc.)
    public boolean canManageTienda(User actor, Long tiendaId) {
        if (isSuperAdmin(actor)) return true;
        return actor.getTienda() != null && actor.getTienda().getId().equals(tiendaId);
    }
}
