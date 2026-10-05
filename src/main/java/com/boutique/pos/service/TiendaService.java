package com.boutique.pos.service;

import com.boutique.pos.dto.TiendaRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public Tienda create(TiendaRequest req, User actor) {
        Tienda t = new Tienda();
        t.setName(req.getName());
        t.setIsActive(true);
        t.setCreatedBy(actor);
        if (tenantScope.isSupervisor(actor)) t.setSupervisor(actor);
        Tienda saved = tiendaRepository.save(t);
        roleService.seedDefaultRolesForTienda(saved);
        return saved;
    }

    /**
     * Actualiza el nombre de una tienda, si el actor puede administrarla.
     *
     * @param id id de la tienda
     * @param req nuevos datos (nombre)
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
        t.setUpdatedBy(actor);
        return tiendaRepository.save(t);
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
