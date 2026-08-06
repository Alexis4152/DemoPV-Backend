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

@Service
@RequiredArgsConstructor
public class TiendaService {

    private final TiendaRepository tiendaRepository;
    private final RoleService roleService;
    private final TenantScope tenantScope;

    public List<Tienda> findAll() {
        return tiendaRepository.findAllByOrderByNameAsc();
    }

    public Tienda findById(Long id) {
        return tiendaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + id));
    }

    public Tienda create(TiendaRequest req) {
        Tienda t = new Tienda();
        t.setName(req.getName());
        t.setIsActive(true);
        Tienda saved = tiendaRepository.save(t);
        roleService.seedDefaultRolesForTienda(saved);
        return saved;
    }

    public Tienda update(Long id, TiendaRequest req) {
        Tienda t = findById(id);
        t.setName(req.getName());
        return tiendaRepository.save(t);
    }

    public void deactivate(Long id) {
        Tienda t = findById(id);
        t.setIsActive(false);
        tiendaRepository.save(t);
    }

    // El color de marca lo puede cambiar el SUPER_ADMIN (cualquier tienda) o el ADMIN
    // de esa misma tienda — nunca el ADMIN de otra tienda.
    public Tienda updateTheme(Long id, String primaryColor, User actor) {
        Tienda t = findById(id);
        if (!tenantScope.canManageTienda(actor, id)) {
            throw new AccessDeniedException("No tienes permiso para modificar el color de esta tienda");
        }
        t.setPrimaryColor(primaryColor);
        return tiendaRepository.save(t);
    }
}
