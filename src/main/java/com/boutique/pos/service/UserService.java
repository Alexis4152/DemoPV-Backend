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

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TiendaRepository tiendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantScope tenantScope;

    public List<User> findAll(User actor) {
        return userRepository.findAllForTienda(tenantScope.scopeId(actor));
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
