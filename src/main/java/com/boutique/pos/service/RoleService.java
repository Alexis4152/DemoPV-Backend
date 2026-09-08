package com.boutique.pos.service;

import com.boutique.pos.dto.RoleRequest;
import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.Role;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.RoleRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CRUD de roles (RBAC) y siembra de los roles por defecto de cada tienda.
 *
 * <p>Un {@link Role} pertenece siempre a una sola tienda (no es global) y define, vía
 * un {@code Set<}{@link AppSection}{@code >}, a qué módulos de la app tienen acceso los
 * usuarios que lo tengan asignado. Cada tienda nueva recibe automáticamente tres roles
 * "de sistema"/base: ADMIN (todas las secciones), CASHIER y SELLER (todas menos USERS y
 * ROLES) — ver {@link #seedDefaultRolesForTienda(Tienda)}.</p>
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TenantScope tenantScope;

    /**
     * Lista los roles activos visibles para el actor: los de su tienda, o todos si es
     * SUPER_ADMIN.
     *
     * <p>Cuando el actor es SUPER_ADMIN, además suma el rol de plataforma SUPERVISOR (que
     * al no pertenecer a ninguna tienda —igual que SUPER_ADMIN mismo— nunca aparecería en
     * un listado acotado a la tienda que esté actuando) para que el selector de rol al dar
     * de alta un usuario en Usuarios.jsx pueda ofrecerlo — es la única forma de crear una
     * cuenta Supervisor desde la aplicación en vez de a mano en la base de datos.</p>
     *
     * @param actor usuario que realiza la consulta
     * @return roles activos dentro del alcance del actor, ordenados por nombre
     */
    public List<Role> findAll(User actor) {
        Long scope = tenantScope.scopeId(actor);
        List<Role> roles = scope == null
                ? new java.util.ArrayList<>(roleRepository.findAllByIsActiveTrueOrderByNameAsc())
                : new java.util.ArrayList<>(roleRepository.findAllByTiendaIdAndIsActiveTrueOrderByNameAsc(scope));
        if (tenantScope.isSuperAdmin(actor)) {
            roleRepository.findFirstByNameAndTiendaIsNullOrderById("SUPERVISOR")
                    .filter(supervisor -> roles.stream().noneMatch(r -> r.getId().equals(supervisor.getId())))
                    .ifPresent(roles::add);
        }
        return roles;
    }

    /**
     * Igual que {@link #findAll(User)} pero paginado, para la pantalla de administración de
     * Roles (a diferencia del método anterior, pensado para selectores que necesitan el
     * catálogo completo, ej. el filtro/formulario de Usuarios).
     *
     * @param actor usuario que realiza la consulta
     * @param pageable página y tamaño solicitados
     * @return página de roles activos dentro del alcance del actor, ordenados por nombre
     */
    public Page<Role> findAll(User actor, Pageable pageable) {
        Long scope = tenantScope.scopeId(actor);
        return scope == null ? roleRepository.findAllByIsActiveTrueOrderByNameAsc(pageable) : roleRepository.findAllByTiendaIdAndIsActiveTrueOrderByNameAsc(scope, pageable);
    }

    /**
     * Busca un rol por id sin validar a qué tienda pertenece. Uso interno; para flujos
     * con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id del rol
     * @return el rol encontrado
     * @throws IllegalArgumentException si no existe
     */
    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + id));
    }

    /**
     * Busca un rol por id validando que pertenezca a la tienda del actor.
     *
     * @param id id del rol
     * @param actor usuario que realiza la consulta
     * @return el rol encontrado, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public Role findById(Long id, User actor) {
        Role r = findById(id);
        // SUPER_ADMIN puede resolver CUALQUIER rol por id, sin importar de qué tienda sea
        // ni cuál tenga elegida como "actuante" — incluye a los roles de plataforma
        // (SUPERVISOR) que de otra forma nunca calificarían (tienda=null no calza con
        // ninguna tienda concreta). No es una escalada de privilegio nueva: SUPER_ADMIN ya
        // puede mandar cualquier tiendaId explícito al dar de alta un usuario (ver
        // UserService#resolveTiendaForWrite), esto solo evita un rechazo inconsistente.
        if (tenantScope.isSuperAdmin(actor)) return r;
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (r.getTienda() == null || !scope.equals(r.getTienda().getId()))) {
            throw new IllegalArgumentException("Rol no encontrado: " + id);
        }
        return r;
    }

    /**
     * Crea un rol nuevo (no de sistema) dentro de la tienda del actor (la que esté
     * actuando, si es SUPER_ADMIN — ver {@link TenantScope#tiendaForWrite}).
     *
     * @param req datos del rol: nombre, descripción y secciones habilitadas
     * @param actor usuario que lo crea; queda como {@code createdBy}
     * @return el rol creado
     * @throws IllegalArgumentException si ya existe un rol con ese nombre en la misma tienda
     * @throws IllegalStateException si es SUPER_ADMIN sin ninguna tienda elegida para actuar
     */
    public Role create(RoleRequest req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null && tenantScope.isPlatformActor(actor)) {
            throw new IllegalStateException("Elige una tienda para poder crear un rol");
        }
        Long tiendaId = tienda != null ? tienda.getId() : null;
        if (roleRepository.existsByNameAndTiendaId(req.getName(), tiendaId)) {
            throw new IllegalArgumentException("Ya existe un rol con ese nombre en tu tienda");
        }
        Role r = new Role();
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        r.setIsSystem(false);
        r.setTienda(tienda);
        r.setSections(sanitizeSections(req.getSections(), false));
        r.setCreatedBy(actor);
        return roleRepository.save(r);
    }

    /**
     * Actualiza un rol existente.
     *
     * <p>Los roles de sistema ({@code isSystem = true}, como ADMIN) no se pueden
     * renombrar, aunque sí se les pueden cambiar descripción y secciones. Para roles
     * normales, un cambio de nombre valida que no choque con otro rol de la misma tienda.</p>
     *
     * @param id id del rol a actualizar
     * @param req nuevos valores: nombre (ignorado si es rol de sistema y no cambia),
     *            descripción y secciones habilitadas
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del rol;
     *              queda registrado como {@code updatedBy}
     * @return el rol actualizado
     * @throws IllegalArgumentException si intenta renombrar un rol de sistema, o si el
     *         nuevo nombre ya existe en la tienda
     */
    public Role update(Long id, RoleRequest req, User actor) {
        Role r = findById(id, actor);
        Long tiendaId = r.getTienda() != null ? r.getTienda().getId() : null;
        if (Boolean.TRUE.equals(r.getIsSystem())) {
            if (req.getName() != null && !req.getName().equals(r.getName())) {
                throw new IllegalArgumentException("No se puede renombrar un rol del sistema");
            }
        } else {
            if (req.getName() != null && !req.getName().equals(r.getName())
                    && roleRepository.existsByNameAndTiendaId(req.getName(), tiendaId)) {
                throw new IllegalArgumentException("Ya existe un rol con ese nombre en tu tienda");
            }
            r.setName(req.getName());
        }
        r.setDescription(req.getDescription());
        r.setSections(sanitizeSections(req.getSections(), Boolean.TRUE.equals(r.getIsSystem())));
        r.setUpdatedBy(actor);
        return roleRepository.save(r);
    }

    // antes borraba la fila; ahora es borrado suave (igual que products/users/tiendas/categories)
    // para poder conservar quién y cuándo lo eliminó.
    /**
     * Elimina (borrado suave) un rol: marca {@code isActive=false} y registra quién y
     * cuándo lo eliminó, sin borrar la fila de la base de datos.
     *
     * <p>No se permite eliminar roles de sistema (ADMIN) ni roles que sigan teniendo
     * usuarios asignados, para no dejar usuarios huérfanos sin rol.</p>
     *
     * @param id id del rol a eliminar
     * @param actor usuario que elimina; debe tener acceso a la tienda del rol
     * @throws IllegalArgumentException si es un rol de sistema, o si aún tiene usuarios asignados
     */
    public void delete(Long id, User actor) {
        Role r = findById(id, actor);
        if (Boolean.TRUE.equals(r.getIsSystem())) {
            throw new IllegalArgumentException("No se puede eliminar un rol del sistema");
        }
        if (userRepository.countByRole(r) > 0) {
            throw new IllegalArgumentException("No se puede eliminar: hay usuarios con este rol asignado");
        }
        r.setIsActive(false);
        r.setDeletedBy(actor);
        r.setDeletedAt(java.time.LocalDateTime.now());
        roleRepository.save(r);
    }

    // usado al crear una tienda nueva y por la migración de roles compartidos a roles por tienda
    /**
     * Crea un rol directamente con los valores dados, sin las validaciones de
     * {@link #create(RoleRequest, User)} (no verifica duplicados ni asigna auditoría de
     * creador). Pensado para siembra de datos: alta de tienda nueva y migración de roles
     * que antes eran compartidos entre tiendas.
     *
     * @param name nombre del rol
     * @param description descripción del rol
     * @param isSystem si es un rol de sistema (no editable/eliminable en su nombre)
     * @param sections secciones habilitadas para el rol
     * @param tienda tienda a la que pertenece el rol
     * @return el rol creado
     */
    public Role createSeedRole(String name, String description, boolean isSystem, Set<AppSection> sections, Tienda tienda) {
        Role r = new Role();
        r.setName(name);
        r.setDescription(description);
        r.setIsSystem(isSystem);
        r.setTienda(tienda);
        r.setSections(new HashSet<>(sections));
        return roleRepository.save(r);
    }

    /**
     * Siembra los tres roles por defecto (ADMIN, CASHIER, SELLER) para una tienda recién
     * creada. ADMIN es de sistema y tiene acceso a todas las secciones; CASHIER y SELLER
     * tienen acceso a todo excepto USERS y ROLES.
     *
     * <p>Es idempotente: si la tienda ya tiene un rol ADMIN no hace nada, para poder
     * llamarse de forma segura sin duplicar roles.</p>
     *
     * @param tienda tienda para la que se siembran los roles
     */
    public void seedDefaultRolesForTienda(Tienda tienda) {
        if (roleRepository.existsByNameAndTiendaId("ADMIN", tienda.getId())) return;

        Set<AppSection> nonAdminSections = EnumSet.allOf(AppSection.class);
        nonAdminSections.remove(AppSection.USERS);
        nonAdminSections.remove(AppSection.ROLES);

        createSeedRole("ADMIN", "Administrador — acceso total", true, EnumSet.allOf(AppSection.class), tienda);
        createSeedRole("CASHIER", "Cajero", false, nonAdminSections, tienda);
        createSeedRole("SELLER", "Vendedor", false, nonAdminSections, tienda);
    }

    /**
     * Normaliza el conjunto de secciones solicitado para un rol: convierte un valor nulo
     * en un conjunto vacío y, si el rol es de sistema, fuerza que incluya siempre ROLES
     * para que el administrador nunca se quede sin acceso a la pantalla de roles (y por
     * ende sin forma de recuperar el acceso).
     *
     * @param requested secciones solicitadas (puede ser null)
     * @param isSystem si el rol es de sistema
     * @return conjunto de secciones saneado
     */
    private Set<AppSection> sanitizeSections(Set<AppSection> requested, boolean isSystem) {
        Set<AppSection> sections = requested == null ? new HashSet<>() : new HashSet<>(requested);
        if (isSystem) {
            // evita que el admin se bloquee a sí mismo la pantalla de roles
            sections.add(AppSection.ROLES);
        }
        return sections;
    }
}
