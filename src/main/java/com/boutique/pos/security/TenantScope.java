package com.boutique.pos.security;

import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Helper central de aislamiento multi-tienda ("Tienda"), usado por prácticamente todos los
 * services de negocio para decidir qué datos puede ver o modificar el usuario que hace la
 * petición ({@code actor}). Encapsula en un solo lugar la regla de que el rol SUPER_ADMIN es
 * la única excepción al aislamiento por tienda: es un usuario de plataforma (sin tienda
 * asignada) que puede ver y administrar todas las tiendas — pero de una en una, "actuando
 * como" ADMIN de la que elija en cada momento (ver {@link #ACTING_TIENDA_HEADER}).
 */
@Component
@RequiredArgsConstructor
public class TenantScope {

    private final TiendaRepository tiendaRepository;

    /**
     * Header HTTP con el id de la tienda sobre la que un SUPER_ADMIN quiere actuar en esta
     * petición (lo manda el frontend tras elegirla en el selector post-login, o al
     * cambiarla desde el sidebar). Para cualquier otro rol se ignora por completo: su
     * tienda siempre es la propia ({@code actor.getTienda()}), sin importar qué venga aquí
     * — un usuario normal no puede "elegir" actuar en otra tienda mandando este header a mano.
     */
    public static final String ACTING_TIENDA_HEADER = "X-Acting-Tienda-Id";

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si su rol es {@code SUPER_ADMIN} (usuario de plataforma, sin
     *         tienda propia, con visibilidad total)
     */
    public boolean isSuperAdmin(User actor) {
        return actor.getRole() != null && "SUPER_ADMIN".equals(actor.getRole().getName());
    }

    /**
     * Lee el id de tienda actuante del request HTTP en curso ({@link
     * #ACTING_TIENDA_HEADER}), si lo hay. Devuelve {@code null} silenciosamente ante
     * cualquier problema (sin request en curso, header ausente, o valor no numérico) —
     * nunca debe tumbar la petición por esto, simplemente se comporta como si el
     * SUPER_ADMIN no hubiera elegido ninguna tienda todavía.
     */
    private Long actingTiendaIdFromRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        String header = attrs.getRequest().getHeader(ACTING_TIENDA_HEADER);
        if (header == null || header.isBlank()) return null;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Calcula el id de tienda por el que se debe filtrar una consulta para este actor.
     *
     * @param actor usuario que realiza la operación
     * @return para SUPER_ADMIN, el id de la tienda que eligió actuar ({@link
     *         #ACTING_TIENDA_HEADER}), o {@code null} si no ha elegido ninguna (sin
     *         filtro, ve todas las tiendas — pensado para vistas agregadas, no para
     *         acciones de escritura); para cualquier otro rol, siempre el id de su
     *         propia tienda
     */
    public Long scopeId(User actor) {
        if (isSuperAdmin(actor)) return actingTiendaIdFromRequest();
        return actor.getTienda() != null ? actor.getTienda().getId() : null;
    }

    /**
     * Tienda sobre la que el actor debe operar al CREAR algo nuevo (producto, venta,
     * categoría, corte de caja, rol...) o al validar límites propios de una tienda
     * (descuentos, etc.). Para cualquier rol normal es siempre la suya. Para SUPER_ADMIN
     * es la tienda que eligió actuar ({@link #ACTING_TIENDA_HEADER}) — puede ser
     * {@code null} si todavía no ha elegido ninguna; el frontend no debería dejarlo llegar
     * hasta aquí (lo manda directo al selector de tienda tras el login), pero cada caller
     * debe estar listo para ese {@code null} de todas formas, normalmente lanzando un
     * {@code IllegalStateException} claro en vez de guardar un registro sin tienda.
     *
     * @param actor usuario que realiza la operación
     * @return la tienda sobre la que debe operar, o {@code null} si es SUPER_ADMIN sin
     *         tienda elegida
     */
    public Tienda tiendaForWrite(User actor) {
        if (!isSuperAdmin(actor)) return actor.getTienda();
        Long actingId = actingTiendaIdFromRequest();
        return actingId != null ? tiendaRepository.findById(actingId).orElse(null) : null;
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
