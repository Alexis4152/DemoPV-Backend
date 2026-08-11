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

/**
 * Administra los datos fiscales y de contacto de una tienda (RFC, dirección, razón
 * social, teléfono, redes sociales, etc.), usados principalmente para imprimirse en el
 * ticket de venta en PDF ({@link TicketPdfService}).
 *
 * <p>Cada tienda tiene a lo más un {@link TiendaInfo}. Solo el ADMIN de esa tienda o un
 * SUPER_ADMIN pueden consultarlos o editarlos, validado con
 * {@link TenantScope#canManageTienda(User, Long)}.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaInfoService {

    private final TiendaInfoRepository tiendaInfoRepository;
    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;

    // Solo el ADMIN de esa tienda (o SUPER_ADMIN) puede ver/editar sus propios datos fiscales.
    /**
     * Obtiene los datos fiscales/de contacto de una tienda.
     *
     * @param tiendaId id de la tienda
     * @param actor usuario que consulta; debe poder administrar esa tienda
     * @return los datos guardados, o un objeto vacío (no persistido) si la tienda
     *         todavía no ha capturado ninguno
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     */
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

    /**
     * Crea o actualiza los datos fiscales/de contacto de una tienda, y de paso
     * actualiza el nombre de la {@link Tienda} asociada (el formulario de datos fiscales
     * también edita el nombre comercial).
     *
     * @param tiendaId id de la tienda
     * @param req nuevos datos fiscales y de contacto
     * @param actor usuario que hace el cambio; debe poder administrar esa tienda; queda
     *              registrado como {@code createdBy} (si es la primera captura) y
     *              {@code updatedBy}
     * @return los datos fiscales ya guardados
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     * @throws IllegalArgumentException si la tienda no existe
     */
    @Transactional
    public TiendaInfo update(Long tiendaId, TiendaInfoRequest req, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        tienda.setName(req.getName());
        tienda.setUpdatedBy(actor);
        tiendaRepository.save(tienda);

        boolean isNew = tiendaInfoRepository.findByTiendaId(tiendaId).isEmpty();
        TiendaInfo info = tiendaInfoRepository.findByTiendaId(tiendaId).orElseGet(() -> {
            TiendaInfo i = new TiendaInfo();
            i.setTienda(tienda);
            return i;
        });
        if (isNew) info.setCreatedBy(actor);
        info.setUpdatedBy(actor);
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
