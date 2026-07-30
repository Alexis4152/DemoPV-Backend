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
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TenantScope tenantScope;

    public List<Role> findAll(User actor) {
        Long scope = tenantScope.scopeId(actor);
        return scope == null ? roleRepository.findAllByOrderByNameAsc() : roleRepository.findAllByTiendaIdOrderByNameAsc(scope);
    }

    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + id));
    }

    public Role findById(Long id, User actor) {
        Role r = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (r.getTienda() == null || !scope.equals(r.getTienda().getId()))) {
            throw new IllegalArgumentException("Rol no encontrado: " + id);
        }
        return r;
    }

    public Role create(RoleRequest req, User actor) {
        Long tiendaId = actor.getTienda() != null ? actor.getTienda().getId() : null;
        if (roleRepository.existsByNameAndTiendaId(req.getName(), tiendaId)) {
            throw new IllegalArgumentException("Ya existe un rol con ese nombre en tu tienda");
        }
        Role r = new Role();
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        r.setIsSystem(false);
        r.setTienda(actor.getTienda());
        r.setSections(sanitizeSections(req.getSections(), false));
        return roleRepository.save(r);
    }

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
        return roleRepository.save(r);
    }

    public void delete(Long id, User actor) {
        Role r = findById(id, actor);
        if (Boolean.TRUE.equals(r.getIsSystem())) {
            throw new IllegalArgumentException("No se puede eliminar un rol del sistema");
        }
        if (userRepository.countByRole(r) > 0) {
            throw new IllegalArgumentException("No se puede eliminar: hay usuarios con este rol asignado");
        }
        roleRepository.delete(r);
    }

    // usado al crear una tienda nueva y por la migración de roles compartidos a roles por tienda
    public Role createSeedRole(String name, String description, boolean isSystem, Set<AppSection> sections, Tienda tienda) {
        Role r = new Role();
        r.setName(name);
        r.setDescription(description);
        r.setIsSystem(isSystem);
        r.setTienda(tienda);
        r.setSections(new HashSet<>(sections));
        return roleRepository.save(r);
    }

    public void seedDefaultRolesForTienda(Tienda tienda) {
        if (roleRepository.existsByNameAndTiendaId("ADMIN", tienda.getId())) return;

        Set<AppSection> nonAdminSections = EnumSet.allOf(AppSection.class);
        nonAdminSections.remove(AppSection.USERS);
        nonAdminSections.remove(AppSection.ROLES);

        createSeedRole("ADMIN", "Administrador — acceso total", true, EnumSet.allOf(AppSection.class), tienda);
        createSeedRole("CASHIER", "Cajero", false, nonAdminSections, tienda);
        createSeedRole("SELLER", "Vendedor", false, nonAdminSections, tienda);
    }

    private Set<AppSection> sanitizeSections(Set<AppSection> requested, boolean isSystem) {
        Set<AppSection> sections = requested == null ? new HashSet<>() : new HashSet<>(requested);
        if (isSystem) {
            // evita que el admin se bloquee a sí mismo la pantalla de roles
            sections.add(AppSection.ROLES);
        }
        return sections;
    }
}
