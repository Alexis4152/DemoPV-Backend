package com.boutique.pos.service;

import com.boutique.pos.dto.UserRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD de usuarios del sistema, con aislamiento por tienda (multi-tenancy) y control de
 * a qué tienda puede asignarse un usuario según quién lo esté dando de alta.
 *
 * <p>Las contraseñas siempre se guardan cifradas vía {@link PasswordEncoder}, nunca en
 * texto plano. La asignación de tienda a un usuario nuevo depende de quién lo crea (ver
 * {@link #resolveTiendaForWrite(UserRequest, User)}): un SUPER_ADMIN puede asignar
 * cualquier tienda (o ninguna, para crear otro usuario de plataforma); cualquier otro
 * actor solo puede dar de alta usuarios dentro de su propia tienda.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantScope tenantScope;

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en SaleService/CashCutService.findAll).
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    /**
     * Busca usuarios aplicando filtros combinables, acotado a la tienda del actor.
     *
     * @param from fecha de alta mínima (inclusiva); si es null se usa un límite inferior
     *             muy antiguo para evitar pasar null al BETWEEN de la consulta
     * @param to fecha de alta máxima (inclusiva); si es null se usa un límite superior
     *           muy lejano, por la misma razón
     * @param name filtro por nombre (parcial), o null para no filtrar
     * @param email filtro por correo (parcial), o null para no filtrar
     * @param roleId filtro por id de rol, o null para no filtrar
     * @param isActive filtro por estado activo/inactivo, o null para no filtrar
     * @param actor usuario que realiza la consulta; acota el resultado a su tienda
     * @return usuarios que cumplen los filtros dentro del alcance del actor
     */
    public List<User> findAll(LocalDateTime from, LocalDateTime to, String name, String email,
                               Long roleId, Boolean isActive, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return userRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, name, email, roleId, isActive);
    }

    /**
     * Busca un usuario por id sin validar a qué tienda pertenece. Uso interno; para
     * flujos con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id del usuario
     * @return el usuario encontrado
     * @throws IllegalArgumentException si no existe
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    /**
     * Busca un usuario por id validando que pertenezca a la tienda del actor.
     *
     * @param id id del usuario
     * @param actor usuario que realiza la consulta
     * @return el usuario encontrado, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public User findById(Long id, User actor) {
        User u = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (u.getTienda() == null || !scope.equals(u.getTienda().getId()))) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }
        return u;
    }

    /**
     * Da de alta un usuario nuevo.
     *
     * <p>La contraseña se cifra antes de guardarse. La tienda del usuario nuevo se
     * resuelve con {@link #resolveTiendaForWrite(UserRequest, User)}: si el actor es
     * SUPER_ADMIN se respeta la tienda solicitada (o ninguna); en cualquier otro caso el
     * usuario nuevo siempre queda en la misma tienda que el actor, sin importar lo que
     * venga en el request.</p>
     *
     * @param req datos del usuario nuevo (nombre, correo, contraseña, rol, tienda deseada)
     * @param actor usuario que lo da de alta; queda registrado como {@code createdBy}
     * @return el usuario creado
     * @throws IllegalArgumentException si el correo ya está registrado
     */
    public User create(UserRequest req, User actor) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(roleService.findById(req.getRoleId(), actor));
        u.setTienda(resolveTiendaForWrite(req, actor));
        u.setIsActive(true);
        u.setCreatedBy(actor);
        return userRepository.save(u);
    }

    /**
     * Actualiza los datos de un usuario existente.
     *
     * <p>La contraseña solo se actualiza si viene un valor no vacío en el request
     * (dejar el campo en blanco significa "no cambiar la contraseña"). El cambio de
     * tienda de un usuario solo lo puede hacer un SUPER_ADMIN; para cualquier otro actor
     * ese campo se ignora aunque venga en el request.</p>
     *
     * @param id id del usuario a actualizar
     * @param req nuevos valores: nombre, correo, contraseña opcional, rol opcional,
     *            tienda opcional (solo aplica si el actor es SUPER_ADMIN)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del usuario
     *              objetivo; queda registrado como {@code updatedBy}
     * @return el usuario actualizado
     */
    public User update(Long id, UserRequest req, User actor) {
        User u = findById(id, actor);
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        if (req.getRoleId() != null) {
            u.setRole(roleService.findById(req.getRoleId(), actor));
        }
        if (tenantScope.isSuperAdmin(actor) && req.getTiendaId() != null) {
            u.setTienda(resolveTienda(req.getTiendaId()));
        }
        u.setUpdatedBy(actor);
        return userRepository.save(u);
    }

    // SUPER_ADMIN puede asignar cualquier tienda (o dejar sin tienda); cualquier otro rol
    // siempre da de alta usuarios dentro de su propia tienda, sin importar lo que venga en el request.
    /**
     * Determina a qué tienda queda asignado un usuario nuevo, según el rol del actor
     * que lo está creando (ver regla de negocio en el comentario anterior).
     *
     * @param req request de alta, con la tienda solicitada (solo se respeta para SUPER_ADMIN)
     * @param actor usuario que da de alta al nuevo usuario
     * @return la tienda resuelta para el usuario nuevo (puede ser null solo si el actor
     *         es SUPER_ADMIN y no especificó tienda)
     */
    private Tienda resolveTiendaForWrite(UserRequest req, User actor) {
        if (tenantScope.isSuperAdmin(actor)) {
            return req.getTiendaId() != null ? resolveTienda(req.getTiendaId()) : null;
        }
        return actor.getTienda();
    }

    private Tienda resolveTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    /**
     * Da de baja (borrado suave) un usuario: marca {@code isActive=false} y registra
     * quién y cuándo lo eliminó, sin borrar la fila.
     *
     * @param id id del usuario a desactivar
     * @param actor usuario que lo desactiva; debe tener acceso a la tienda del usuario objetivo
     */
    public void deactivate(Long id, User actor) {
        User u = findById(id, actor);
        u.setIsActive(false);
        u.setDeletedBy(actor);
        u.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(u);
    }
}
