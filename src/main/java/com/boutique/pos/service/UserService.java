package com.boutique.pos.service;

import com.boutique.pos.dto.UserRequest;
import com.boutique.pos.model.CashCutStatus;
import com.boutique.pos.model.Role;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.CashCutRepository;
import com.boutique.pos.repository.RoleRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final RoleRepository roleRepository;
    private final TiendaRepository tiendaRepository;
    private final CashCutRepository cashCutRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantScope tenantScope;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    // Misma property que ya usa AuthService para el link de recuperación de contraseña —
    // en local cae en localhost:5173 por default; en prod se fija con la variable de
    // entorno APP_FRONTEND_URL al dominio real donde quede desplegado el frontend.
    @Value("${app.frontend.url}")
    private String frontendUrl;

    // BETWEEN siempre necesita las dos fechas: Postgres no logra inferir el tipo de un
    // parámetro timestamp nulo (mismo caso que en SaleService/CashCutService.findAll).
    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);

    // Sin caracteres ambiguos (0/O, 1/l/I) para que quien la teclee a mano desde el correo
    // no se equivoque adivinando cuál es cuál.
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    // Jerarquía de roles para decidir quién puede asignar/administrar a quién: cada actor
    // solo puede dar de alta, editar el rol de, o dar de baja usuarios con un rol
    // ESTRICTAMENTE por debajo del suyo — nunca uno de su mismo nivel ni uno superior (ver
    // assertCanAssignRole/assertCanManage). Los tres roles de sistema tienen un rango fijo;
    // cualquier otro nombre (CASHIER, SELLER, o un rol personalizado que un ADMIN cree
    // desde "Roles y Permisos") cae en UNRANKED_ROLE, por debajo de ADMIN.
    private static final Map<String, Integer> ROLE_RANK = Map.of(
            "SUPER_ADMIN", 0,
            "SUPERVISOR", 1,
            "ADMIN", 2
    );
    private static final int UNRANKED_ROLE = 3;

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
     * @param pageable página y tamaño solicitados
     * @return página de usuarios que cumplen los filtros dentro del alcance del actor
     */
    public Page<User> findAll(LocalDateTime from, LocalDateTime to, String name, String email,
                               Long roleId, Boolean isActive, User actor, Pageable pageable) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        // Los usuarios con rol de plataforma (SUPER_ADMIN, SUPERVISOR) no tienen tienda
        // (tienda_id null), así que nunca calzarían con el filtro normal de "tienda que
        // esté actuando" — sin este caso especial, filtrar por ese rol en Usuarios.jsx
        // siempre regresaría vacío aunque sí existan, algo confuso para el SUPER_ADMIN que
        // acaba de dar de alta un Supervisor y quiere volver a encontrarlo.
        Long scope = tenantScope.isSuperAdmin(actor) && isPlatformRole(roleId) ? null : tenantScope.scopeId(actor);
        return userRepository.search(scope, effectiveFrom, effectiveTo, name, email, roleId, isActive, pageable);
    }

    /** {@code true} si el id de rol dado corresponde a un rol de plataforma (ver {@link
     *  #isPlatformRole(Role)}); {@code false} (nunca truena) si el id es null o no existe. */
    private boolean isPlatformRole(Long roleId) {
        if (roleId == null) return false;
        try {
            return roleService.findById(roleId).getTienda() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
     * Indica si el correo dado pertenece a un usuario existente pero DESACTIVADO — usado
     * por {@code UserController#create} únicamente para decidir el texto de la respuesta
     * ("Usuario creado" vs. "Usuario reactivado"), sin duplicar la lógica real de {@link
     * #create}, que es quien decide y ejecuta la reactivación.
     *
     * @param email correo a comprobar
     * @return {@code true} si existe un usuario con ese correo y está inactivo
     */
    public boolean isReactivatableEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> !Boolean.TRUE.equals(u.getIsActive()))
                .orElse(false);
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
        // SUPER_ADMIN puede ver CUALQUIER usuario por id, sin importar en qué tienda esté
        // actuando — incluye a otros usuarios de plataforma (SUPERVISOR) que, al no tener
        // tienda, nunca calzarían con el filtro normal. Mismo criterio que RoleService#findById.
        if (tenantScope.isSuperAdmin(actor)) return u;
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (u.getTienda() == null || !scope.equals(u.getTienda().getId()))) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }
        return u;
    }

    /** Rango de un rol en la jerarquía (ver {@link #ROLE_RANK}); menor número = más poder. */
    private int rankOf(Role role) {
        return role != null ? ROLE_RANK.getOrDefault(role.getName(), UNRANKED_ROLE) : UNRANKED_ROLE;
    }

    // "Hacia abajo, nunca uno como él o arriba": un SUPERVISOR puede crear ADMIN/CASHIER/
    // SELLER/roles personalizados pero nunca otro SUPERVISOR ni SUPER_ADMIN; un ADMIN puede
    // crear CASHIER/SELLER/personalizados pero YA NO otro ADMIN (antes de esta regla sí
    // podía, sin querer). SUPER_ADMIN puede crear cualquier rol excepto otro SUPER_ADMIN
    // (esa alta sigue siendo, a propósito, un paso manual fuera de la aplicación).
    /**
     * Valida que el actor tenga permiso para asignar el rol dado a un usuario (nuevo o
     * existente), según la jerarquía de {@link #ROLE_RANK}.
     *
     * @param target rol que se quiere asignar
     * @param actor  usuario que hace la asignación
     * @throws AccessDeniedException si el rol del actor no es estrictamente superior al
     *         rol que intenta asignar
     */
    private void assertCanAssignRole(Role target, User actor) {
        if (rankOf(target) <= rankOf(actor.getRole())) {
            throw new AccessDeniedException("No puedes asignar un rol igual o superior al tuyo");
        }
    }

    /**
     * Valida que el actor tenga permiso para editar o dar de baja a un usuario ya
     * existente (mismo criterio que {@link #assertCanAssignRole}, aplicado al rol ACTUAL
     * del usuario objetivo en vez de a uno nuevo) — exceptúa editar la propia cuenta, que
     * siempre tiene el mismo rango que el actor y de otra forma quedaría bloqueada.
     *
     * @param target usuario sobre el que se quiere actuar
     * @param actor  usuario que intenta editarlo/darlo de baja
     * @throws AccessDeniedException si el rol del actor no es estrictamente superior al
     *         del usuario objetivo
     */
    private void assertCanManage(User target, User actor) {
        if (target.getId().equals(actor.getId())) return;
        if (rankOf(target.getRole()) <= rankOf(actor.getRole())) {
            throw new AccessDeniedException("No puedes administrar una cuenta de tu mismo nivel o superior");
        }
    }

    /**
     * Da de alta un usuario nuevo — o, si el correo pertenece a uno ya existente pero
     * DESACTIVADO (borrado suave, ver {@link #deactivate}), lo reactiva en vez de crear un
     * registro duplicado.
     *
     * <p>La razón: {@code email} es {@code UNIQUE} y el borrado es siempre suave (la fila
     * nunca desaparece), así que un correo que ya perteneció a alguien dado de baja no
     * puede "liberarse" para un alta de verdad — sin este caso especial, el admin se
     * encontraría con "El correo ya está registrado" al querer recontratar a la misma
     * persona o reasignarle el correo a alguien más, sin poder hacer nada al respecto.
     * Reactivar conserva el mismo id (y con él, todo lo que ya referencia a ese usuario:
     * ventas, cortes de caja, auditoría) en vez de perder ese historial en un registro
     * "muerto" y arrancar uno nuevo desde cero.</p>
     *
     * <p>Si el correo pertenece a un usuario activo, sí es un error real (ver {@code
     * throws}). La contraseña NUNCA la captura quien da de alta o reactiva: siempre se
     * genera una temporal al azar ({@link #generateTempPassword()}) y se le manda por
     * correo (ver {@link EmailService#sendNewUserPasswordEmail}), junto con el aviso de
     * que se le va a pedir cambiarla al iniciar sesión ({@link User#getMustChangePassword()}
     * queda en {@code true}) — igual en ambos casos, alta o reactivación. La tienda se
     * resuelve con {@link #resolveTiendaForWrite(UserRequest, User)}: si el actor es
     * SUPER_ADMIN se respeta la tienda solicitada (o ninguna); si es SUPERVISOR, siempre la
     * que esté actuando; en cualquier otro caso el usuario siempre queda en la misma tienda
     * que el actor, sin importar lo que venga en el request. También valida, vía {@link
     * #assertCanAssignRole}, que el rol solicitado quede estrictamente por debajo del rol
     * del actor en la jerarquía del sistema (ver {@link #ROLE_RANK}).</p>
     *
     * @param req datos del usuario (nombre, correo, rol, tienda deseada — el campo
     *            {@code password} del request, si viene, se ignora a propósito); si
     *            reactiva a alguien, nombre/rol/tienda se actualizan con estos valores
     *            (como un update normal sobre ese registro)
     * @param actor usuario que lo da de alta/reactiva; queda registrado como {@code
     *              createdBy} (alta nueva) o {@code updatedBy} (reactivación)
     * @return el usuario creado o reactivado
     * @throws IllegalArgumentException si el correo ya está registrado y ACTIVO
     */
    public User create(UserRequest req, User actor) {
        Optional<User> existing = userRepository.findByEmail(req.getEmail());
        if (existing.isPresent()) {
            User found = existing.get();
            if (Boolean.TRUE.equals(found.getIsActive())) {
                throw new IllegalArgumentException("El correo ya está registrado");
            }
            return reactivate(found, req, actor);
        }
        Role role = roleService.findById(req.getRoleId(), actor);
        assertCanAssignRole(role, actor);
        Tienda tienda = resolveTiendaForWrite(req, actor, role);
        if (tienda == null && tenantScope.isPlatformActor(actor) && !isPlatformRole(role)) {
            throw new IllegalStateException("Elige una tienda para poder dar de alta un usuario");
        }
        String tempPassword = generateTempPassword();
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(tempPassword));
        u.setMustChangePassword(true);
        u.setRole(role);
        u.setTienda(tienda);
        u.setIsActive(true);
        u.setCreatedBy(actor);
        User saved = userRepository.save(u);
        applySupervisedTiendas(saved, req.getSupervisedTiendaIds(), actor);
        emailService.sendNewUserPasswordEmail(saved, tempPassword, frontendUrl + "/login");
        return saved;
    }

    /**
     * Reactiva un usuario previamente desactivado, sobre EL MISMO registro (mismo id) —
     * ver {@link #create} para el porqué. Limpia el rastro de la baja anterior ({@code
     * deletedAt}/{@code deletedBy}) y aplica nombre/rol/tienda tal como si fuera un alta
     * nueva, con una contraseña temporal nueva (la anterior, si alguna vez la cambió, ya
     * no sirve para nada de todas formas al estar desactivado).
     *
     * @param u usuario existente, actualmente {@code isActive=false}
     * @param req nuevos datos (nombre, rol, tienda deseada)
     * @param actor quien reactiva; queda registrado como {@code updatedBy}
     * @return el mismo usuario, ya reactivado
     */
    private User reactivate(User u, UserRequest req, User actor) {
        Role role = roleService.findById(req.getRoleId(), actor);
        assertCanAssignRole(role, actor);
        Tienda tienda = resolveTiendaForWrite(req, actor, role);
        if (tienda == null && tenantScope.isPlatformActor(actor) && !isPlatformRole(role)) {
            throw new IllegalStateException("Elige una tienda para poder reactivar este usuario");
        }
        String tempPassword = generateTempPassword();
        u.setName(req.getName());
        u.setPassword(passwordEncoder.encode(tempPassword));
        u.setMustChangePassword(true);
        u.setRole(role);
        u.setTienda(tienda);
        u.setIsActive(true);
        u.setDeletedAt(null);
        u.setDeletedBy(null);
        u.setUpdatedBy(actor);
        User saved = userRepository.save(u);
        applySupervisedTiendas(saved, req.getSupervisedTiendaIds(), actor);
        emailService.sendNewUserPasswordEmail(saved, tempPassword, frontendUrl + "/login");
        return saved;
    }

    /**
     * Actualiza los datos de un usuario existente.
     *
     * <p>La contraseña solo se actualiza si viene un valor no vacío en el request
     * (dejar el campo en blanco significa "no cambiar la contraseña") — y cuando el admin
     * SÍ la cambia por esta vía, igual que al dar de alta, el usuario no la eligió, así
     * que también queda marcado {@code mustChangePassword=true}. El correo se valida
     * único igual que en {@link #create} (excluyendo al propio usuario, para no rechazar
     * guardar sin haber tocado el correo). El cambio de tienda de un usuario solo lo puede
     * hacer SUPER_ADMIN (a cualquier tienda) o SUPERVISOR (solo a una de las suyas — ver
     * {@link TenantScope#canManageTienda}); para cualquier otro actor ese campo se ignora
     * aunque venga en el request. Un cambio de tienda REAL (a una distinta de la actual) se
     * rechaza si el usuario tiene un corte de caja abierto — moverlo dejaría ese corte
     * apuntando a una tienda de la que ya no es parte, sin nadie ahí que pueda cerrarlo.</p>
     *
     * @param id id del usuario a actualizar
     * @param req nuevos valores: nombre, correo, contraseña opcional, rol opcional,
     *            tienda opcional (solo aplica si el actor es SUPER_ADMIN o SUPERVISOR)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del usuario
     *              objetivo; queda registrado como {@code updatedBy}
     * @return el usuario actualizado
     * @throws IllegalArgumentException si el correo ya lo usa OTRO usuario
     * @throws AccessDeniedException si un SUPERVISOR intenta mover al usuario a una tienda
     *         que no administra
     * @throws IllegalStateException si el usuario tiene un corte de caja abierto y se le
     *         intenta cambiar a una tienda distinta de la actual
     */
    public User update(Long id, UserRequest req, User actor) {
        User u = findById(id, actor);
        assertCanManage(u, actor);
        userRepository.findByEmail(req.getEmail())
                .filter(existing -> !existing.getId().equals(u.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("El correo ya está registrado"); });
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
            u.setMustChangePassword(true);
        }
        if (req.getRoleId() != null) {
            Role role = roleService.findById(req.getRoleId(), actor);
            // Editar su PROPIO rol no pasa por la jerarquía (assertCanManage ya lo exceptúa
            // arriba por la misma razón: el actor siempre tiene su propio rango, así que la
            // regla "estrictamente por debajo" lo bloquearía a él mismo sin querer).
            if (!u.getId().equals(actor.getId())) assertCanAssignRole(role, actor);
            u.setRole(role);
            // Ascenderlo a un rol de plataforma (ej. ADMIN -> SUPERVISOR) le quita la tienda
            // fija que tuviera — de lo contrario un Supervisor quedaría con una tienda propia
            // además de las que administre por Tienda.supervisor, algo sin sentido en el
            // diseño (ver isPlatformRole). El caso contrario (bajarlo de un rol de
            // plataforma) se resuelve un poco más abajo, junto con el tiendaId explícito.
            if (isPlatformRole(role)) u.setTienda(null);
        }
        if (req.getTiendaId() != null && !isPlatformRole(u.getRole())
                && (tenantScope.isSuperAdmin(actor) || tenantScope.isSupervisor(actor))) {
            // SUPER_ADMIN puede mover a cualquier tienda; SUPERVISOR solo a una de las
            // suyas — canManageTienda ya sabe distinguir ambos casos.
            if (!tenantScope.canManageTienda(actor, req.getTiendaId())) {
                throw new AccessDeniedException("Esa tienda no está bajo tu administración");
            }
            Tienda target = resolveTienda(req.getTiendaId());
            Long currentTiendaId = u.getTienda() != null ? u.getTienda().getId() : null;
            // Un corte de caja abierto queda ligado a la tienda donde se abrió (ver
            // CashCut.tienda) — si al usuario que lo abrió lo movemos a otra tienda, ese
            // corte quedaría "huérfano": nadie con acceso a esa tienda podría cerrarlo
            // normalmente. Solo bloquea un cambio REAL de tienda, no guardar sin tocarla.
            if (!target.getId().equals(currentTiendaId)
                    && cashCutRepository.findFirstByUserIdAndStatus(u.getId(), CashCutStatus.OPEN).isPresent()) {
                throw new IllegalStateException(
                        "No puedes cambiar de tienda a un usuario con un corte de caja abierto. Ciérralo primero.");
            }
            u.setTienda(target);
        } else if (!isPlatformRole(u.getRole()) && u.getTienda() == null) {
            // Se le quitó (o nunca tuvo) una tienda propia y su rol actual SÍ la necesita —
            // típicamente al "bajar" a alguien de SUPERVISOR/SUPER_ADMIN a un rol normal sin
            // mandar tiendaId explícito. Sin este resguardo quedaría huérfano (rol normal,
            // tienda null), lo que además le da acceso sin filtro en varias consultas que
            // interpretan "sin tienda" como "sin restricción" (ver TenantScope#scopeId).
            Tienda fallback = tenantScope.tiendaForWrite(actor);
            if (fallback == null) {
                throw new IllegalStateException("Elige una tienda para poder asignarle este rol a este usuario");
            }
            u.setTienda(fallback);
        }
        // Un rol normal (no de plataforma) pertenece a una tienda específica — la fila de
        // Role está duplicada por tienda (ver RoleService#seedDefaultRolesForTienda), así
        // que "CASHIER de la tienda 6" y "CASHIER de la tienda 7" son registros distintos
        // aunque se llamen igual. Si el usuario terminó en una tienda distinta de la de su
        // rol actual (típicamente por el cambio de tienda de arriba), busca el rol del
        // MISMO NOMBRE en la tienda nueva y lo reasigna — de lo contrario quedaría con un
        // rol "importado" de la tienda anterior, invisible e inmodificable desde "Roles y
        // Permisos" de su tienda nueva.
        if (!isPlatformRole(u.getRole()) && u.getTienda() != null) {
            Long roleTiendaId = u.getRole().getTienda() != null ? u.getRole().getTienda().getId() : null;
            if (!u.getTienda().getId().equals(roleTiendaId)) {
                Role matching = roleRepository.findByNameAndTiendaId(u.getRole().getName(), u.getTienda().getId())
                        .orElseThrow(() -> new IllegalStateException(
                                "La tienda destino no tiene un rol llamado \"" + u.getRole().getName()
                                        + "\" — créalo ahí primero o cambia el rol del usuario antes de moverlo."));
                u.setRole(matching);
            }
        }
        u.setUpdatedBy(actor);
        User saved = userRepository.save(u);
        applySupervisedTiendas(saved, req.getSupervisedTiendaIds(), actor);
        return saved;
    }

    /**
     * Genera una contraseña temporal aleatoria y criptográficamente segura, para dar de
     * alta un usuario nuevo (ver {@link #create}). Evita caracteres ambiguos (0/O, 1/l/I)
     * para que, si alguien la teclea a mano leyéndola del correo, no dude entre cuál es cuál.
     */
    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(secureRandom.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // SUPER_ADMIN: si el request trae una tienda explícita se respeta esa (permite, p. ej.,
    // "mover" a alguien a una tienda distinta de la que está actuando); si no, cae en la
    // tienda que esté actuando (ver TenantScope#tiendaForWrite). Cualquier otro rol
    // siempre da de alta usuarios dentro de su propia tienda, sin importar lo que venga en
    // el request.
    /**
     * Determina a qué tienda queda asignado un usuario nuevo, según el rol del actor
     * que lo está creando (ver regla de negocio en el comentario anterior).
     *
     * @param req request de alta, con la tienda solicitada (solo se respeta para SUPER_ADMIN)
     * @param actor usuario que da de alta al nuevo usuario
     * @param role rol que se le va a asignar — si es un rol de plataforma (SUPER_ADMIN,
     *             SUPERVISOR) el resultado siempre es {@code null}: esas cuentas nunca
     *             tienen una tienda propia fija, sin importar qué tienda esté "actuando"
     *             quien las da de alta ni qué venga en el request
     * @return la tienda resuelta para el usuario nuevo (puede ser null también si el actor
     *         es SUPER_ADMIN/SUPERVISOR, no especificó tienda en el request, Y tampoco
     *         tiene ninguna tienda válida eligiendo actuar)
     */
    private Tienda resolveTiendaForWrite(UserRequest req, User actor, Role role) {
        if (isPlatformRole(role)) return null;
        if (tenantScope.isSuperAdmin(actor)) {
            return req.getTiendaId() != null ? resolveTienda(req.getTiendaId()) : tenantScope.tiendaForWrite(actor);
        }
        // SUPERVISOR no tiene tienda propia (igual que SUPER_ADMIN) — el nuevo usuario
        // siempre queda en la tienda que esté actuando ahora mismo, nunca en una elegida
        // libremente por request (a diferencia de SUPER_ADMIN, no puede "mover" usuarios a
        // cualquier tienda, solo operar dentro de las que ya tiene asignadas).
        if (tenantScope.isSupervisor(actor)) {
            return tenantScope.tiendaForWrite(actor);
        }
        return actor.getTienda();
    }

    /** {@code true} si el rol dado es uno "de plataforma" (SUPER_ADMIN o SUPERVISOR): sin
     *  tienda propia por diseño, ver {@link #resolveTiendaForWrite}. */
    private boolean isPlatformRole(Role role) {
        return role != null && ("SUPER_ADMIN".equals(role.getName()) || "SUPERVISOR".equals(role.getName()));
    }

    private Tienda resolveTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    // Reemplaza POR COMPLETO el conjunto de tiendas que un Supervisor administra — quitar
    // (supervisor=null) las que ya no vienen en la lista nueva, asignar las que faltan. Solo
    // el SUPER_ADMIN puede tocar esto, y solo tiene sentido sobre un usuario con rol
    // SUPERVISOR — cualquier otra combinación no hace nada, en vez de fallar (para no
    // tronar una edición normal de un usuario que de casualidad manda este campo en null/vacío).
    /**
     * Aplica (reemplazando por completo) el conjunto de tiendas que un usuario SUPERVISOR
     * administra, tomado de {@link UserRequest#getSupervisedTiendaIds()}.
     *
     * Si el usuario YA NO tiene rol SUPERVISOR (ej. se le cambió el rol en esta misma
     * edición), en vez de nada más ignorar {@code supervisedTiendaIds} le quita todas las
     * tiendas que tuviera asignadas — de lo contrario quedarían apuntando como "supervisada
     * por" alguien que ya no es Supervisor.
     *
     * @param target usuario ya guardado al que se le va a fijar la asignación
     * @param supervisedTiendaIds tiendas deseadas, o {@code null} para no tocar la
     *                            asignación actual (ej. una edición que no incluye este
     *                            campo); una lista vacía sí quita todas las que tuviera
     * @param actor quien hace el cambio; se ignora silenciosamente si no es SUPER_ADMIN
     */
    private void applySupervisedTiendas(User target, List<Long> supervisedTiendaIds, User actor) {
        if (!tenantScope.isSuperAdmin(actor)) return;
        boolean isSupervisorNow = target.getRole() != null && "SUPERVISOR".equals(target.getRole().getName());
        if (!isSupervisorNow) {
            for (Tienda t : tiendaRepository.findBySupervisorIdOrderByNameAsc(target.getId())) {
                t.setSupervisor(null);
                tiendaRepository.save(t);
            }
            return;
        }
        if (supervisedTiendaIds == null) return;

        List<Tienda> current = tiendaRepository.findBySupervisorIdOrderByNameAsc(target.getId());
        List<Tienda> desired = supervisedTiendaIds.isEmpty() ? List.of() : tiendaRepository.findAllById(supervisedTiendaIds);

        for (Tienda t : current) {
            if (desired.stream().noneMatch(d -> d.getId().equals(t.getId()))) {
                t.setSupervisor(null);
                tiendaRepository.save(t);
            }
        }
        for (Tienda t : desired) {
            Long currentSupervisorId = t.getSupervisor() != null ? t.getSupervisor().getId() : null;
            if (!target.getId().equals(currentSupervisorId)) {
                t.setSupervisor(target);
                tiendaRepository.save(t);
            }
        }
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
        assertCanManage(u, actor);
        u.setIsActive(false);
        u.setDeletedBy(actor);
        u.setDeletedAt(java.time.LocalDateTime.now());
        userRepository.save(u);
    }
}
