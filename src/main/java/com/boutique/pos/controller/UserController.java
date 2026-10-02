package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.PageResponse;
import com.boutique.pos.dto.UserRequest;
import com.boutique.pos.model.User;
import com.boutique.pos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controlador de usuarios, expuesto bajo {@code /api/users}.
 * <p>
 * Administra los usuarios (cajeros, vendedores, administradores, etc.) de la
 * tienda del usuario autenticado, incluyendo su asignación de rol. Todos los métodos
 * requieren acceso a la sección {@code USERS} (el {@code @PreAuthorize} de la clase); dar
 * de alta, editar, desactivar y forzar el cierre de sesión de un usuario además exigen el
 * permiso fino correspondiente ({@code USERS:CREATE}/{@code EDIT}/{@code DELETE} — ver el
 * {@code @PreAuthorize} de cada uno de esos métodos, que repite la sección porque un
 * {@code @PreAuthorize} a nivel de método reemplaza al de la clase en vez de sumarse; roles
 * de gestión lo tienen implícito, ver {@code SectionAccessService#checkAction}).
 * <p>
 * Esta anotación es solo la primera compuerta. La que de verdad decide quién puede crear,
 * editar o dar de baja a QUIÉN (y no solo "si puede usar el módulo") es la jerarquía de
 * {@link UserService} (ver {@code assertCanAssignRole}/{@code assertCanManage}): un actor
 * solo puede administrar usuarios con un rol estrictamente por debajo del suyo, salvo entre
 * dos roles sin rango (CASHIER o uno personalizado), que si pueden administrarse entre sí.
 * Así, aunque un rol personalizado tenga aquí el permiso {@code USERS:CREATE}, nunca podrá
 * crear/editar/eliminar una cuenta ADMIN/SUPERVISOR/SUPER_ADMIN — esa jerarquía no se toca
 * desde "Roles y Permisos".
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('USERS')")
public class UserController {

    private final UserService userService;

    /**
     * Lista los usuarios de la tienda del usuario autenticado, con filtros
     * opcionales por rango de fecha de alta, nombre, email, rol y estado activo.
     *
     * @param from     fecha/hora inicial del rango de alta (opcional)
     * @param to       fecha/hora final del rango de alta (opcional)
     * @param name     nombre a filtrar (opcional)
     * @param email    email a filtrar (opcional)
     * @param roleId   id de rol a filtrar (opcional)
     * @param isActive filtra por usuarios activos/inactivos (opcional)
     * @param page     número de página, 0-based (default 0)
     * @param size     tamaño de página (default 20)
     * @param actor    usuario autenticado; determina el filtro por tienda
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<User>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> result = userService.findAll(from, to, name, email, roleId, isActive, actor, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result), null));
    }

    /**
     * Obtiene el detalle de un usuario por su id, dentro de la tienda del
     * usuario autenticado.
     *
     * @param id    identificador del usuario
     * @param actor usuario autenticado
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id, actor), null));
    }

    /**
     * Crea un nuevo usuario en la tienda del usuario autenticado.
     *
     * @param req   datos del usuario a crear (incluye el rol asignado)
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    @PreAuthorize("@sectionAccess.checkAction('USERS', 'CREATE')")
    public ResponseEntity<ApiResponse<User>> create(@Valid @RequestBody UserRequest req, @AuthenticationPrincipal User actor) {
        // Se checa ANTES de crear/reactivar (que es quien de verdad decide y ejecuta) solo
        // para poder avisarle al admin qué pasó de verdad — ver UserService#create.
        boolean reactivating = userService.isReactivatableEmail(req.getEmail());
        User saved = userService.create(req, actor);
        String message = reactivating
                ? "Ese correo ya tenía un usuario desactivado — se reactivó en vez de crear uno nuevo"
                : "Usuario creado";
        return ResponseEntity.ok(ApiResponse.ok(saved, message));
    }

    /**
     * Actualiza los datos de un usuario existente.
     *
     * @param id    identificador del usuario a actualizar
     * @param req   nuevos datos del usuario
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('USERS', 'EDIT')")
    public ResponseEntity<ApiResponse<User>> update(@PathVariable Long id,
                                                     @Valid @RequestBody UserRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, req, actor), "Usuario actualizado"));
    }

    /**
     * Desactiva (baja lógica) un usuario existente.
     *
     * @param id    identificador del usuario a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@sectionAccess.checkAction('USERS', 'DELETE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        userService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Usuario desactivado"));
    }

    /**
     * Cierra a la fuerza la sesión abierta de un usuario (ver {@link UserService#forceLogout}
     * para el detalle de qué tan inmediato es esto en realidad).
     *
     * @param id    identificador del usuario a desloguear
     * @param actor usuario autenticado que ejecuta la acción
     */
    @PostMapping("/{id}/force-logout")
    @PreAuthorize("@sectionAccess.checkAction('USERS', 'EDIT')")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        userService.forceLogout(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Sesión cerrada"));
    }
}
