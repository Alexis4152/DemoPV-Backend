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
 * la tienda en sí (alta, nombre, color, baja); los datos fiscales/de contacto viven en
 * {@link TiendaInfoService} y el logo en {@link TiendaLogoService}.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaService {

    private final TiendaRepository tiendaRepository;
    private final RoleService roleService;
    private final TenantScope tenantScope;

    /**
     * Lista todas las tiendas, ordenadas por nombre. Sin filtro por tenant: pensado para
     * uso de SUPER_ADMIN (por ejemplo, el selector de tienda al dar de alta un usuario).
     *
     * @return todas las tiendas
     */
    public List<Tienda> findAll() {
        return tiendaRepository.findAllByOrderByNameAsc();
    }

    /**
     * Busca una tienda por id.
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
     * Da de alta una tienda nueva y le siembra sus roles por defecto (ADMIN, CASHIER,
     * SELLER) vía {@link RoleService#seedDefaultRolesForTienda(Tienda)}, para que quede
     * lista para operar (dar de alta usuarios, productos, etc.) de inmediato.
     *
     * @param req datos de la tienda (nombre)
     * @param actor usuario que la crea (normalmente SUPER_ADMIN); queda registrado como
     *              {@code createdBy}
     * @return la tienda creada, ya con sus roles base sembrados
     */
    public Tienda create(TiendaRequest req, User actor) {
        Tienda t = new Tienda();
        t.setName(req.getName());
        t.setIsActive(true);
        t.setCreatedBy(actor);
        Tienda saved = tiendaRepository.save(t);
        roleService.seedDefaultRolesForTienda(saved);
        return saved;
    }

    /**
     * Actualiza el nombre de una tienda.
     *
     * @param id id de la tienda
     * @param req nuevos datos (nombre)
     * @param actor usuario que hace el cambio; queda registrado como {@code updatedBy}
     * @return la tienda actualizada
     */
    public Tienda update(Long id, TiendaRequest req, User actor) {
        Tienda t = findById(id);
        t.setName(req.getName());
        t.setUpdatedBy(actor);
        return tiendaRepository.save(t);
    }

    /**
     * Da de baja (borrado suave) una tienda: marca {@code isActive=false} y registra
     * quién y cuándo, sin borrar la fila ni sus datos relacionados.
     *
     * @param id id de la tienda a desactivar
     * @param actor usuario que la desactiva
     */
    public void deactivate(Long id, User actor) {
        Tienda t = findById(id);
        t.setIsActive(false);
        t.setDeletedBy(actor);
        t.setDeletedAt(java.time.LocalDateTime.now());
        tiendaRepository.save(t);
    }

    // El color de marca lo puede cambiar el SUPER_ADMIN (cualquier tienda) o el ADMIN
    // de esa misma tienda — nunca el ADMIN de otra tienda.
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
