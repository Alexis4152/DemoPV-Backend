package com.boutique.pos.service;

import com.boutique.pos.dto.TiendaInfoRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.TiendaInfo;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaInfoRepository;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TiendaInfoService {

    private final TiendaInfoRepository tiendaInfoRepository;
    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;

    // Solo el ADMIN de esa tienda (o SUPER_ADMIN) puede ver/editar sus propios datos fiscales.
    public TiendaInfo get(Long tiendaId, User actor) {
        checkAccess(tiendaId, actor);
        return tiendaInfoRepository.findByTiendaId(tiendaId).orElseGet(() -> blankFor(tiendaId));
    }

    // Objeto vacío (no guardado) para que el formulario tenga algo que mostrar
    // cuando la tienda todavía no ha capturado sus datos.
    private TiendaInfo blankFor(Long tiendaId) {
        Tienda tienda = findTienda(tiendaId);
        TiendaInfo info = new TiendaInfo();
        info.setTienda(tienda);
        return info;
    }

    @Transactional
    public TiendaInfo update(Long tiendaId, TiendaInfoRequest req, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        tienda.setName(req.getName());
        tiendaRepository.save(tienda);

        TiendaInfo info = tiendaInfoRepository.findByTiendaId(tiendaId).orElseGet(() -> {
            TiendaInfo i = new TiendaInfo();
            i.setTienda(tienda);
            return i;
        });
        info.setRfc(req.getRfc());
        info.setCalle(req.getCalle());
        info.setColonia(req.getColonia());
        info.setCodigoPostal(req.getCodigoPostal());
        info.setLocalidad(req.getLocalidad());
        info.setEstado(req.getEstado());
        info.setRazonSocial(req.getRazonSocial());
        info.setTelefono(req.getTelefono());
        info.setPaginaWeb(req.getPaginaWeb());
        info.setRedesSociales(req.getRedesSociales());
        info.setNotasAdicionales(req.getNotasAdicionales());
        return tiendaInfoRepository.save(info);
    }

    private Tienda findTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    private void checkAccess(Long tiendaId, User actor) {
        if (!tenantScope.canManageTienda(actor, tiendaId)) {
            throw new AccessDeniedException("No tienes permiso sobre los datos de esta tienda");
        }
    }
}
