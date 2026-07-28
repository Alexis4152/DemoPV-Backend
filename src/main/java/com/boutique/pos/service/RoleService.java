package com.boutique.pos.service;

import com.boutique.pos.dto.RoleRequest;
import com.boutique.pos.model.AppSection;
import com.boutique.pos.model.Role;
import com.boutique.pos.repository.RoleRepository;
import com.boutique.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public List<Role> findAll() {
        return roleRepository.findAllByOrderByNameAsc();
    }

    public Role findById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + id));
    }

    public Role create(RoleRequest req) {
        if (roleRepository.existsByName(req.getName())) {
            throw new IllegalArgumentException("Ya existe un rol con ese nombre");
        }
        Role r = new Role();
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        r.setIsSystem(false);
        r.setSections(sanitizeSections(req.getSections(), false));
        return roleRepository.save(r);
    }

    public Role update(Long id, RoleRequest req) {
        Role r = findById(id);
        if (Boolean.TRUE.equals(r.getIsSystem())) {
            if (req.getName() != null && !req.getName().equals(r.getName())) {
                throw new IllegalArgumentException("No se puede renombrar un rol del sistema");
            }
        } else {
            if (req.getName() != null && !req.getName().equals(r.getName())
                    && roleRepository.existsByName(req.getName())) {
                throw new IllegalArgumentException("Ya existe un rol con ese nombre");
            }
            r.setName(req.getName());
        }
        r.setDescription(req.getDescription());
        r.setSections(sanitizeSections(req.getSections(), Boolean.TRUE.equals(r.getIsSystem())));
        return roleRepository.save(r);
    }

    public void delete(Long id) {
        Role r = findById(id);
        if (Boolean.TRUE.equals(r.getIsSystem())) {
            throw new IllegalArgumentException("No se puede eliminar un rol del sistema");
        }
        if (userRepository.countByRole(r) > 0) {
            throw new IllegalArgumentException("No se puede eliminar: hay usuarios con este rol asignado");
        }
        roleRepository.delete(r);
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
