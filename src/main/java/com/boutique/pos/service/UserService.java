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

    public List<User> findAll(LocalDateTime from, LocalDateTime to, String name, String email,
                               Long roleId, Boolean isActive, User actor) {
        LocalDateTime effectiveFrom = from != null ? from : MIN_DATE;
        LocalDateTime effectiveTo = to != null ? to : MAX_DATE;
        return userRepository.search(tenantScope.scopeId(actor), effectiveFrom, effectiveTo, name, email, roleId, isActive);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    public User findById(Long id, User actor) {
        User u = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (u.getTienda() == null || !scope.equals(u.getTienda().getId()))) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }
        return u;
    }

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
        return userRepository.save(u);
    }

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
        return userRepository.save(u);
    }

    // SUPER_ADMIN puede asignar cualquier tienda (o dejar sin tienda); cualquier otro rol
    // siempre da de alta usuarios dentro de su propia tienda, sin importar lo que venga en el request.
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

    public void deactivate(Long id, User actor) {
        User u = findById(id, actor);
        u.setIsActive(false);
        userRepository.save(u);
    }
}
