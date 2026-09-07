package com.boutique.pos.service;

import com.boutique.pos.dto.UserRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.repository.UserRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
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
    private final TiendaRepository tiendaRepository;
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
        return userRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, name, email, roleId, isActive, pageable);
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
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (u.getTienda() == null || !scope.equals(u.getTienda().getId()))) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }
        return u;
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
     * SUPER_ADMIN se respeta la tienda solicitada (o ninguna); en cualquier otro caso el
     * usuario siempre queda en la misma tienda que el actor, sin importar lo que venga en
     * el request — esto también permite "mover" a un usuario reactivado a otra tienda si
     * hace falta.</p>
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
        String tempPassword = generateTempPassword();
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(tempPassword));
        u.setMustChangePassword(true);
        u.setRole(roleService.findById(req.getRoleId(), actor));
        u.setTienda(resolveTiendaForWrite(req, actor));
        u.setIsActive(true);
        u.setCreatedBy(actor);
        User saved = userRepository.save(u);
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
        String tempPassword = generateTempPassword();
        u.setName(req.getName());
        u.setPassword(passwordEncoder.encode(tempPassword));
        u.setMustChangePassword(true);
        u.setRole(roleService.findById(req.getRoleId(), actor));
        u.setTienda(resolveTiendaForWrite(req, actor));
        u.setIsActive(true);
        u.setDeletedAt(null);
        u.setDeletedBy(null);
        u.setUpdatedBy(actor);
        User saved = userRepository.save(u);
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
     * hacer un SUPER_ADMIN; para cualquier otro actor ese campo se ignora aunque venga en
     * el request.</p>
     *
     * @param id id del usuario a actualizar
     * @param req nuevos valores: nombre, correo, contraseña opcional, rol opcional,
     *            tienda opcional (solo aplica si el actor es SUPER_ADMIN)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda del usuario
     *              objetivo; queda registrado como {@code updatedBy}
     * @return el usuario actualizado
     * @throws IllegalArgumentException si el correo ya lo usa OTRO usuario
     */
    public User update(Long id, UserRequest req, User actor) {
        User u = findById(id, actor);
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
            u.setRole(roleService.findById(req.getRoleId(), actor));
        }
        if (tenantScope.isSuperAdmin(actor) && req.getTiendaId() != null) {
            u.setTienda(resolveTienda(req.getTiendaId()));
        }
        u.setUpdatedBy(actor);
        return userRepository.save(u);
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
