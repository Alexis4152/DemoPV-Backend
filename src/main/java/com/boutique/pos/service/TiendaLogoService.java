package com.boutique.pos.service;

import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.TiendaRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// Guarda el logo de cada tienda en disco local (dev: carpeta del proyecto backend;
// prod: la misma ruta pero apuntando al disco del server) y deja la ruta pública
// (servida vía /uploads/**, ver StaticResourceConfig) en Tienda.logoPath.
// Si una tienda no sube logo, logoPath se queda null y el frontend usa el de Nexora.
/**
 * Administra la subida y borrado del logo de marca de cada tienda.
 *
 * <p>Solo se acepta PNG/JPEG/WEBP hasta 3&nbsp;MB. Solo el ADMIN de la tienda o un
 * SUPER_ADMIN pueden subir o quitar el logo. El logo también se usa en el ticket de
 * venta en PDF ({@link TicketPdfService}), que cae al logo por defecto de Nexora si la
 * tienda no tiene uno propio. El guardado en disco en sí, incluyendo la validación de
 * tipo/tamaño, se delega en {@link ImageStorageService}, compartido con {@link
 * ProductImageService}.</p>
 */
@Service
@RequiredArgsConstructor
public class TiendaLogoService {

    private static final long MAX_SIZE_BYTES = 3L * 1024 * 1024; // 3 MB

    private final TiendaRepository tiendaRepository;
    private final TenantScope tenantScope;
    private final ImageStorageService imageStorageService;

    /**
     * Sube (o reemplaza) el logo de una tienda.
     *
     * <p>Valida tipo de archivo (PNG/JPEG/WEBP) y tamaño máximo (3&nbsp;MB), guarda el
     * archivo nuevo con un nombre único, borra el archivo del logo anterior si existía
     * (best effort — un fallo al borrar solo se registra en el log) y actualiza
     * {@code Tienda.logoPath} con la ruta pública servida vía {@code /uploads/**}.</p>
     *
     * @param tiendaId id de la tienda
     * @param file archivo de imagen subido (PNG, JPEG o WEBP, máx. 3 MB)
     * @param actor usuario que sube el logo; debe poder administrar esa tienda; queda
     *              registrado como {@code updatedBy}
     * @return la tienda con {@code logoPath} actualizado
     * @throws IllegalArgumentException si el archivo viene vacío, no es de un tipo
     *         permitido o excede el tamaño máximo
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     * @throws RuntimeException si falla la escritura del archivo a disco
     */
    @Transactional
    public Tienda upload(Long tiendaId, MultipartFile file, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);

        String path = imageStorageService.store(file, "logos", "tienda-" + tiendaId + "-", MAX_SIZE_BYTES);

        imageStorageService.delete(tienda.getLogoPath());
        tienda.setLogoPath(path);
        tienda.setUpdatedBy(actor);
        return tiendaRepository.save(tienda);
    }

    /**
     * Quita el logo de una tienda: borra el archivo en disco (best effort) y limpia
     * {@code Tienda.logoPath}. Tras esto, el frontend vuelve a mostrar el logo por
     * defecto de Nexora.
     *
     * @param tiendaId id de la tienda
     * @param actor usuario que quita el logo; debe poder administrar esa tienda; queda
     *              registrado como {@code updatedBy}
     * @return la tienda con {@code logoPath} en null
     * @throws org.springframework.security.access.AccessDeniedException si el actor no
     *         tiene permiso sobre esa tienda
     */
    @Transactional
    public Tienda remove(Long tiendaId, User actor) {
        checkAccess(tiendaId, actor);
        Tienda tienda = findTienda(tiendaId);
        imageStorageService.delete(tienda.getLogoPath());
        tienda.setLogoPath(null);
        tienda.setUpdatedBy(actor);
        return tiendaRepository.save(tienda);
    }

    private Tienda findTienda(Long tiendaId) {
        return tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new IllegalArgumentException("Tienda no encontrada: " + tiendaId));
    }

    private void checkAccess(Long tiendaId, User actor) {
        if (!tenantScope.canManageTienda(actor, tiendaId)) {
            throw new AccessDeniedException("No tienes permiso sobre el logo de esta tienda");
        }
    }
}
