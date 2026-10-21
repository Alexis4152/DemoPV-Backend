package com.boutique.pos.service;

import com.boutique.pos.dto.TiendaPaymentConfigRequest;
import com.boutique.pos.dto.TiendaRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * CRUD de tiendas (sucursales/negocios) y gestión de su color de marca (theming).
 *
 * <p>Una {@link Tienda} es la unidad raíz del multi-tenancy: todo (productos, ventas,
 * cortes de caja, roles, usuarios) cuelga de una tienda. Este servicio solo administra
 * la tienda en sí (alta, nombre, color, baja, y a qué SUPERVISOR le pertenece); los datos
 * fiscales/de contacto viven en {@link TiendaInfoService} y el logo en {@link
 * TiendaLogoService}.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaService {

    private final TiendaRepository tiendaRepository;
    private final RoleService roleService;
    private final TenantScope tenantScope;

    /**
     * Lista las tiendas visibles para el actor: todas (SUPER_ADMIN) o solo las que tenga
     * asignadas (SUPERVISOR, ver {@link Tienda#getSupervisor()}), ordenadas por nombre.
     *
     * <p>Si {@code supervisorId} viene y el actor es SUPER_ADMIN, en vez de "todas" regresa
     * las tiendas asignadas a ESE Supervisor en particular — usado por Usuarios.jsx para
     * precargar, al editar un Supervisor existente, cuáles tiendas ya administra (el propio
     * usuario no trae esa info: es la relación inversa, vive en {@code Tienda.supervisor}).
     * Para cualquier otro actor se ignora, y cae en el comportamiento normal de arriba.</p>
     *
     * @param actor usuario que consulta (SUPER_ADMIN o SUPERVISOR — el controller ya
     *              restringe el acceso a este endpoint a esos dos roles)
     * @param supervisorId id de un Supervisor cuyas tiendas se quieren ver en vez de todas
     *                     (solo tiene efecto si el actor es SUPER_ADMIN), o null para el
     *                     comportamiento normal
     * @return tiendas dentro del alcance del actor
     */
    public List<Tienda> findAll(User actor, Long supervisorId) {
        if (supervisorId != null && tenantScope.isSuperAdmin(actor)) {
            return tiendaRepository.findBySupervisorIdOrderByNameAsc(supervisorId);
        }
        if (tenantScope.isSuperAdmin(actor)) return tiendaRepository.findAllByOrderByNameAsc();
        return tiendaRepository.findBySupervisorIdOrderByNameAsc(actor.getId());
    }

    /**
     * Busca una tienda por id, sin validar si el actor puede administrarla. Uso interno
     * (ej. otros services que ya resolvieron el id por su cuenta); para flujos con control
     * de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id de la tienda
     * @return la tienda encontrada
     * @throws IllegalArgumentException si no existe
     */
    public Tienda findById(Long id) {
        return tiendaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + id));
    }

    /**
     * Busca una tienda por id, validando que el actor pueda administrarla (ver {@link
     * TenantScope#canManageTienda}) — para un SUPERVISOR, evita que consulte el detalle de
     * una tienda que no le pertenece con solo cambiar el id en la URL.
     *
     * @param id    id de la tienda
     * @param actor usuario que consulta
     * @return la tienda encontrada
     * @throws IllegalArgumentException si no existe
     * @throws AccessDeniedException si el actor no puede administrar esa tienda
     */
    public Tienda findById(Long id, User actor) {
        Tienda t = findById(id);
        if (!tenantScope.canManageTienda(actor, id)) {
            throw new AccessDeniedException("No tienes permiso para ver esta tienda");
        }
        return t;
    }

    /**
     * Da de alta una tienda nueva y le siembra sus roles por defecto (ADMIN, CASHIER,
     * SELLER) vía {@link RoleService#seedDefaultRolesForTienda(Tienda)}, para que quede
     * lista para operar (dar de alta usuarios, productos, etc.) de inmediato.
     *
     * <p>Si quien la crea es un SUPERVISOR, la tienda queda asignada a él automáticamente
     * (ver {@link Tienda#getSupervisor()}) — de lo contrario nacería sin nadie que pudiera
     * verla en su selector de tiendas. Si la crea SUPER_ADMIN, queda sin supervisor
     * asignado (se le puede asignar uno después editando la tienda).</p>
     *
     * @param req datos de la tienda (nombre)
     * @param actor usuario que la crea (SUPER_ADMIN o SUPERVISOR); queda registrado como
     *              {@code createdBy}
     * @return la tienda creada, ya con sus roles base sembrados
     */
    @Transactional
    public Tienda create(TiendaRequest req, User actor) {
        if (tenantScope.isSupervisor(actor)) {
            Integer max = actor.getMaxTiendas();
            long used = tiendaRepository.countBySupervisorIdAndIsActiveTrue(actor.getId());
            if (max != null && used >= max) {
                throw new IllegalStateException("Tu plan contratado te permite capturar hasta " + max + " tienda"
                        + (max == 1 ? "" : "s") + " y ya tienes " + used + ". Para agregar más, pide que amplíen tu plan.");
            }
        }
        Tienda t = new Tienda();
        t.setName(req.getName());
        t.setIsActive(true);
        t.setCardPaymentEnabled(false);
        t.setOpenpayEnabled(false);
        if (tenantScope.isSuperAdmin(actor)) {
            if (req.getCardPaymentEnabled() != null) {
                t.setCardPaymentEnabled(req.getCardPaymentEnabled());
            }
            if (req.getOpenpayEnabled() != null) {
                t.setOpenpayEnabled(req.getOpenpayEnabled());
            }
        }
        t.setCreatedBy(actor);
        if (tenantScope.isSupervisor(actor)) t.setSupervisor(actor);
        Tienda saved = tiendaRepository.save(t);
        roleService.seedDefaultRolesForTienda(saved);
        return saved;
    }

    /**
     * Plan del SUPERVISOR: máximo de tiendas que puede tener ({@code null} = sin límite) y
     * cuántas activas tiene. Para SUPER_ADMIN no aplica (sin límite).
     */
    public Map<String, Object> plan(User actor) {
        Map<String, Object> plan = new HashMap<>();
        if (tenantScope.isSupervisor(actor)) {
            plan.put("maxTiendas", actor.getMaxTiendas());
            plan.put("usedTiendas", tiendaRepository.countBySupervisorIdAndIsActiveTrue(actor.getId()));
        } else {
            plan.put("maxTiendas", null);
            plan.put("usedTiendas", null);
        }
        return plan;
    }

    /**
     * Actualiza el nombre de una tienda, si el actor puede administrarla. Si el actor es
     * {@code SUPER_ADMIN}, también permite actualizar {@code cardPaymentEnabled} y
     * {@code openpayEnabled}.
     *
     * @param id id de la tienda
     * @param req nuevos datos (nombre y, opcionalmente para SUPER_ADMIN, banderas de pago)
     * @param actor usuario que hace el cambio; queda registrado como {@code updatedBy}
     * @return la tienda actualizada
     * @throws AccessDeniedException si el actor no puede administrar esa tienda
     */
    public Tienda update(Long id, TiendaRequest req, User actor) {
        Tienda t = findById(id);
        if (!tenantScope.canManageTienda(actor, id)) {
            throw new AccessDeniedException("No tienes permiso para modificar esta tienda");
        }
        t.setName(req.getName());
        if (tenantScope.isSuperAdmin(actor)) {
            if (req.getCardPaymentEnabled() != null) {
                t.setCardPaymentEnabled(req.getCardPaymentEnabled());
            }
            if (req.getOpenpayEnabled() != null) {
                t.setOpenpayEnabled(req.getOpenpayEnabled());
            }
        }
        t.setUpdatedBy(actor);
        return tiendaRepository.save(t);
    }

    /**
     * Habilita o deshabilita el pago con tarjeta (terminal física) y/o la pasarela Openpay
     * para una o N tiendas de forma masiva. Exclusivo del rol {@code SUPER_ADMIN}.
     *
     * @param req ids de las tiendas a modificar y los estados de {@code cardPaymentEnabled}
     *            y/o {@code openpayEnabled}
     * @param actor usuario autenticado; debe ser {@code SUPER_ADMIN}
     * @return lista de tiendas actualizadas
     * @throws AccessDeniedException si el actor no tiene el rol {@code SUPER_ADMIN}
     */
    @Transactional
    public List<Tienda> updatePaymentMethods(TiendaPaymentConfigRequest req, User actor) {
        if (!tenantScope.isSuperAdmin(actor)) {
            throw new AccessDeniedException("Solo el SUPER_ADMIN puede habilitar o deshabilitar métodos de pago por tienda");
        }
        List<Tienda> tiendas = tiendaRepository.findAllById(req.getTiendaIds());
        if (tiendas.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron las tiendas seleccionadas");
        }
        for (Tienda t : tiendas) {
            if (req.getCardPaymentEnabled() != null) {
                t.setCardPaymentEnabled(req.getCardPaymentEnabled());
            }
            if (req.getOpenpayEnabled() != null) {
                t.setOpenpayEnabled(req.getOpenpayEnabled());
            }
            t.setUpdatedBy(actor);
        }
        return tiendaRepository.saveAll(tiendas);
    }

    /**
     * Da de baja (borrado suave) una tienda: marca {@code isActive=false} y registra
     * quién y cuándo, sin borrar la fila ni sus datos relacionados. Solo si el actor puede
     * administrarla.
     *
     * @param id id de la tienda a desactivar
     * @param actor usuario que la desactiva
     * @throws AccessDeniedException si el actor no puede administrar esa tienda
     */
    public void deactivate(Long id, User actor) {
        Tienda t = findById(id);
        if (!tenantScope.canManageTienda(actor, id)) {
            throw new AccessDeniedException("No tienes permiso para desactivar esta tienda");
        }
        t.setIsActive(false);
        t.setDeletedBy(actor);
        t.setDeletedAt(java.time.LocalDateTime.now());
        tiendaRepository.save(t);
    }

    // El color de marca lo puede cambiar SUPER_ADMIN/SUPERVISOR (sobre las suyas) o el
    // ADMIN de esa misma tienda — nunca el ADMIN de otra tienda.
    /**
     * Actualiza el color primario de marca de una tienda, usado para personalizar la UI
     * del frontend con la identidad de cada negocio.
     *
     * @param id id de la tienda
     * @param primaryColor nuevo color primario (formato esperado por el frontend, p. ej. hex)
     * @param actor usuario que hace el cambio; debe poder administrar esa tienda; queda
     *              registrado como {@code updatedBy}
     * @return la tienda con el color actualizado
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     */
    public Tienda updateTheme(Long id, String primaryColor, User actor) {
        Tienda t = findById(id);
        if (!tenantScope.canManageTienda(actor, id)) {
            throw new AccessDeniedException("No tienes permiso para modificar el color de esta tienda");
        }
        t.setPrimaryColor(primaryColor);
        t.setUpdatedBy(actor);
        return tiendaRepository.save(t);
    }
}
