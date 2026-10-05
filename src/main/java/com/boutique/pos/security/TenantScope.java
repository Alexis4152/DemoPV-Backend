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
 * petición ({@code actor}). Encapsula en un solo lugar las dos excepciones al aislamiento
 * estricto por tienda:
 * <ul>
 *   <li>{@code SUPER_ADMIN}: usuario de plataforma (sin tienda asignada) que ve y administra
 *   TODAS las tiendas, de una en una, "actuando como" ADMIN de la que elija en cada momento
 *   (ver {@link #ACTING_TIENDA_HEADER}).</li>
 *   <li>{@code SUPERVISOR}: igual mecanismo de "elegir tienda y actuar como su ADMIN", pero
 *   acotado a un SUBCONJUNTO de tiendas — las que tengan a este usuario como {@link
 *   Tienda#getSupervisor()}. A diferencia de SUPER_ADMIN, aquí SÍ hace falta verificar la
 *   pertenencia antes de confiar en el header (ver {@link #verifiedSupervisorTienda}):
 *   nada le impide a un Supervisor mandar el id de una tienda ajena a mano.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantScope {

    private final TiendaRepository tiendaRepository;

    /**
     * Header HTTP con el id de la tienda sobre la que un SUPER_ADMIN o SUPERVISOR quiere
     * actuar en esta petición (lo manda el frontend tras elegirla en el selector post-login,
     * o al cambiarla desde el sidebar). Para cualquier otro rol se ignora por completo: su
     * tienda siempre es la propia ({@code actor.getTienda()}), sin importar qué venga aquí
     * — un usuario normal no puede "elegir" actuar en otra tienda mandando este header a mano.
     */
    public static final String ACTING_TIENDA_HEADER = "X-Acting-Tienda-Id";

    // Ningún id real de tienda puede ser negativo (BIGSERIAL siempre empieza en 1), así que
    // esto nunca hace match con ninguna fila real — ver el porqué en scopeId().
    private static final Long NO_MATCH_TIENDA_ID = -1L;

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si su rol es {@code SUPER_ADMIN} (usuario de plataforma, sin
     *         tienda propia, con visibilidad total)
     */
    public boolean isSuperAdmin(User actor) {
        return actor.getRole() != null && "SUPER_ADMIN".equals(actor.getRole().getName());
    }

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si su rol es {@code SUPERVISOR} ("Supervisor de tiendas": usuario
     *         de plataforma sin tienda propia, con visibilidad total sobre el subconjunto de
     *         tiendas que tenga asignadas — ver {@link Tienda#getSupervisor()})
     */
    public boolean isSupervisor(User actor) {
        return actor.getRole() != null && "SUPERVISOR".equals(actor.getRole().getName());
    }

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si es SUPER_ADMIN o SUPERVISOR — los dos roles "de plataforma"
     *         que no tienen una tienda propia fija y en cambio actúan sobre la que elijan
     *         vía {@link #ACTING_TIENDA_HEADER}. Útil para los guards de "elige una tienda
     *         antes de poder crear esto", iguales para ambos.
     */
    public boolean isPlatformActor(User actor) {
        return isSuperAdmin(actor) || isSupervisor(actor);
    }

    /**
     * @param actor usuario que realiza la operación
     * @return {@code true} si el actor tiene permisos de nivel ADMIN sobre la tienda en la
     *         que está actuando: su rol es literalmente {@code ADMIN}, o es SUPER_ADMIN/
     *         SUPERVISOR actuando como tal. Pensado para las reglas de negocio que hoy
     *         distinguen "ADMIN o superior" de "cajero/vendedor" (ej. abrir varios cortes de
     *         caja el mismo día, ajustar stock en negativo).
     */
    public boolean isAdminLevel(User actor) {
        if (isSuperAdmin(actor) || isSupervisor(actor)) return true;
        return actor.getRole() != null && "ADMIN".equals(actor.getRole().getName());
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
     * Resuelve la tienda actuante de un SUPERVISOR, verificando que de verdad esté
     * asignada a él ({@link Tienda#getSupervisor()}) — a diferencia de SUPER_ADMIN, que
     * puede actuar sobre cualquier tienda por diseño, un Supervisor NO puede simplemente
     * mandar el id de una tienda ajena en el header y que se le confíe. Devuelve
     * {@code null} si no hay header, no es numérico, la tienda no existe, o no le pertenece
     * — en todos los casos se trata igual que "no ha elegido ninguna tienda todavía".
     */
    private Tienda verifiedSupervisorTienda(User actor) {
        Long actingId = actingTiendaIdFromRequest();
        if (actingId == null) return null;
        return tiendaRepository.findById(actingId)
                .filter(t -> t.getSupervisor() != null && t.getSupervisor().getId().equals(actor.getId()))
                .orElse(null);
    }

    /**
     * Calcula el id de tienda por el que se debe filtrar una consulta para este actor.
     *
     * @param actor usuario que realiza la operación
     * @return para SUPER_ADMIN, el id de la tienda que eligió actuar ({@link
     *         #ACTING_TIENDA_HEADER}), sin verificar pertenencia (puede actuar sobre
     *         cualquiera), {@code null} si no ha elegido ninguna (sin filtro, ve todas las
     *         tiendas — pensado para vistas agregadas, no para acciones de escritura); para
     *         SUPERVISOR, el id de la tienda elegida si de verdad le pertenece, o si no
     *         {@link #NO_MATCH_TIENDA_ID} — a propósito NUNCA {@code null}: para él, "sin
     *         tienda válida elegida" nunca debe significar "sin filtro, ve todo el sistema"
     *         (ese significado es exclusivo de SUPER_ADMIN, que sí puede ver todo por
     *         diseño) — la mayoría de los repositorios interpretan un {@code tiendaId} nulo
     *         como "no filtrar", así que aquí un id imposible garantiza cero resultados en
     *         vez de exponer datos de tiendas ajenas; para cualquier otro rol, siempre el id
     *         de su propia tienda
     */
    public Long scopeId(User actor) {
        if (isSuperAdmin(actor)) return actingTiendaIdFromRequest();
        if (isSupervisor(actor)) {
            Tienda t = verifiedSupervisorTienda(actor);
            return t != null ? t.getId() : NO_MATCH_TIENDA_ID;
        }
        return actor.getTienda() != null ? actor.getTienda().getId() : null;
    }

    /**
     * Tienda sobre la que el actor debe operar al CREAR algo nuevo (producto, venta,
     * categoría, corte de caja, rol...) o al validar límites propios de una tienda
     * (descuentos, etc.). Para cualquier rol normal es siempre la suya. Para SUPER_ADMIN es
     * la tienda que eligió actuar ({@link #ACTING_TIENDA_HEADER}), sin restricción; para
     * SUPERVISOR, igual pero solo si esa tienda le pertenece (ver {@link
     * #verifiedSupervisorTienda}). Puede ser {@code null} si es SUPER_ADMIN/SUPERVISOR sin
     * tienda elegida (o eligiendo una que no le pertenece); el frontend no debería dejarlo
     * llegar hasta aquí (lo manda directo al selector de tienda tras el login), pero cada
     * caller debe estar listo para ese {@code null} de todas formas, normalmente lanzando un
     * {@code IllegalStateException} claro en vez de guardar un registro sin tienda.
     *
     * @param actor usuario que realiza la operación
     * @return la tienda sobre la que debe operar, o {@code null} si no tiene ninguna elegida
     */
    public Tienda tiendaForWrite(User actor) {
        if (isSupervisor(actor)) return verifiedSupervisorTienda(actor);
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
     * @return {@code true} si es SUPER_ADMIN (puede administrar cualquier tienda), si es
     *         SUPERVISOR y esa tienda le pertenece, o si el actor pertenece exactamente a
     *         esa tienda; {@code false} en cualquier otro caso
     */
    public boolean canManageTienda(User actor, Long tiendaId) {
        if (isSuperAdmin(actor)) return true;
        if (isSupervisor(actor)) {
            return tiendaRepository.findById(tiendaId)
                    .map(t -> t.getSupervisor() != null && t.getSupervisor().getId().equals(actor.getId()))
                    .orElse(false);
        }
        return actor.getTienda() != null && actor.getTienda().getId().equals(tiendaId);
    }
}
