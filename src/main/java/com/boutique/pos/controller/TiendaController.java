package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.TiendaInfoRequest;
import com.boutique.pos.dto.TiendaRequest;
import com.boutique.pos.dto.TiendaThemeRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.TiendaInfo;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ApartadoPromoPdfService;
import com.boutique.pos.service.TiendaInfoService;
import com.boutique.pos.service.TiendaLogoService;
import com.boutique.pos.service.TiendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controlador de tiendas (sucursales/negocios), expuesto bajo {@code /api/tiendas}.
 * <p>
 * La administración general de tiendas (alta, baja, edición de datos base) es de los dos
 * roles "de plataforma": SUPER_ADMIN (cualquier tienda) y SUPERVISOR (solo las tiendas que
 * tenga asignadas — ver {@link com.boutique.pos.security.TenantScope}), según el {@code
 * @PreAuthorize} definido a nivel de clase; el filtro de "cuáles puede ver/tocar" lo aplica
 * el service, no la anotación. Además, varios métodos de personalización (tema/color de
 * marca, datos fiscales y logo) declaran su propio {@code @PreAuthorize} sumando también al
 * ADMIN de esa tienda: en Spring Security el {@code @PreAuthorize} de método
 * <b>reemplaza</b> al de la clase (no se combinan), por lo que en esos endpoints el ADMIN
 * puede operar sobre SU PROPIA tienda igual que SUPER_ADMIN/SUPERVISOR sobre las suyas.
 */
@RestController
@RequestMapping("/api/tiendas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPERVISOR')")
public class TiendaController {

    private final TiendaService tiendaService;
    private final TiendaInfoService tiendaInfoService;
    private final TiendaLogoService tiendaLogoService;
    private final ApartadoPromoPdfService apartadoPromoPdfService;

    /**
     * Lista las tiendas visibles para el actor: todas si es SUPER_ADMIN, o solo las que
     * tenga asignadas si es SUPERVISOR.
     *
     * @param supervisorId opcional; si viene y el actor es SUPER_ADMIN, en vez de "todas"
     *                     regresa las tiendas asignadas a ese Supervisor en particular (ver
     *                     {@link TiendaService#findAll(User, Long)})
     * @param actor usuario autenticado que consulta
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Tienda>>> list(
            @RequestParam(required = false) Long supervisorId,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findAll(actor, supervisorId), null));
    }

    /**
     * Obtiene el detalle de una tienda por su id, si el actor puede administrarla (ver
     * {@link com.boutique.pos.security.TenantScope#canManageTienda}).
     *
     * @param id    identificador de la tienda
     * @param actor usuario autenticado que consulta
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findById(id, actor), null));
    }

    /**
     * Da de alta una nueva tienda en el sistema. Disponible para SUPER_ADMIN y SUPERVISOR;
     * si quien la crea es un SUPERVISOR, queda asignada a él automáticamente (ver {@link
     * TiendaService#create}).
     *
     * @param req   datos de la tienda a crear
     * @param actor usuario autenticado que realiza la creación
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Tienda>> create(@Valid @RequestBody TiendaRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.create(req, actor), "Tienda creada"));
    }

    /**
     * Actualiza los datos base de una tienda existente, si el actor puede administrarla.
     *
     * @param id    identificador de la tienda a actualizar
     * @param req   nuevos datos de la tienda
     * @param actor usuario autenticado que realiza la actualización
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> update(@PathVariable Long id, @Valid @RequestBody TiendaRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.update(id, req, actor), "Tienda actualizada"));
    }

    /**
     * Desactiva (baja lógica) una tienda existente, si el actor puede administrarla.
     *
     * @param id    identificador de la tienda a desactivar
     * @param actor usuario autenticado que realiza la baja
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        tiendaService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tienda desactivada"));
    }

    // El ADMIN de una tienda también puede cambiar SU propio color de marca —
    // este método sobreescribe el @PreAuthorize de la clase.
    /**
     * Actualiza el color primario ("tema"/marca) de una tienda. Este método declara su
     * propio {@code @PreAuthorize} sumando también a ADMIN, reemplazando (no combinando) el
     * de la clase, para que el ADMIN pueda personalizar el color de SU PROPIA tienda; el
     * service es responsable de impedir que edite tiendas ajenas.
     *
     * @param id    identificador de la tienda
     * @param req   nuevo color primario
     * @param actor usuario autenticado que realiza el cambio
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PutMapping("/{id}/theme")
    public ResponseEntity<ApiResponse<Tienda>> updateTheme(
            @PathVariable Long id,
            @Valid @RequestBody TiendaThemeRequest req,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.updateTheme(id, req.getPrimaryColor(), actor), "Color actualizado"));
    }

    // Datos fiscales/de contacto y logo — igual que /theme, el ADMIN de esa tienda
    // (o SUPER_ADMIN/SUPERVISOR sobre las suyas) puede leer y editar, nunca de una ajena.
    /**
     * Obtiene los datos fiscales y de contacto de una tienda. Al igual que {@code /theme},
     * sobreescribe el {@code @PreAuthorize} de clase para permitir el acceso al ADMIN de
     * esa misma tienda, además de SUPER_ADMIN/SUPERVISOR.
     *
     * @param id    identificador de la tienda
     * @param actor usuario autenticado
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @GetMapping("/{id}/info")
    public ResponseEntity<ApiResponse<TiendaInfo>> getInfo(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.get(id, actor), null));
    }

    /**
     * Actualiza los datos fiscales y de contacto de una tienda. Accesible para el ADMIN de
     * esa misma tienda o para SUPER_ADMIN/SUPERVISOR sobre las suyas (ver nota de
     * {@link #updateTheme} sobre la sobreescritura del {@code @PreAuthorize} de clase).
     *
     * @param id    identificador de la tienda
     * @param req   nuevos datos fiscales/de contacto
     * @param actor usuario autenticado que realiza la actualización
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PutMapping("/{id}/info")
    public ResponseEntity<ApiResponse<TiendaInfo>> updateInfo(
            @PathVariable Long id,
            @Valid @RequestBody TiendaInfoRequest req,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.update(id, req, actor), "Datos de la tienda actualizados"));
    }

    /**
     * Sube o reemplaza el logo de una tienda. Accesible para el ADMIN de esa misma tienda o
     * para SUPER_ADMIN/SUPERVISOR sobre las suyas.
     * Genera un PDF promocional de una sola página con el QR de la vitrina pública de
     * apartados de la tienda (ver {@link ApartadoPromoPdfService}), para imprimir o
     * compartir. Mismo candado de acceso que {@code /info}: el ADMIN de esa tienda o
     * SUPER_ADMIN.
     *
     * @param id  identificador de la tienda
     * @param url URL pública completa de su vitrina (ej. {@code https://.../apartar/mi-tienda}),
     *            armada por el frontend — el backend no conoce su propio dominio público
     * @param actor usuario autenticado
     * @throws IllegalStateException si la tienda no tiene los apartados habilitados (con
     *         su slug definido) — no tiene caso generar un QR a un link que no funciona
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/apartados-promo.pdf")
    public ResponseEntity<byte[]> apartadosPromoPdf(
            @PathVariable Long id,
            @RequestParam String url,
            @AuthenticationPrincipal User actor
    ) {
        TiendaInfo info = tiendaInfoService.get(id, actor);
        Tienda tienda = info.getTienda();
        if (!Boolean.TRUE.equals(tienda.getApartadosEnabled()) || tienda.getPublicSlug() == null || tienda.getPublicSlug().isBlank()) {
            throw new IllegalStateException("Habilita la tienda pública de apartados antes de generar el PDF promocional");
        }
        byte[] pdf = apartadoPromoPdfService.generate(tienda, url);
        String filename = "apartados-" + tienda.getPublicSlug() + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /**
     * Sube o reemplaza el logo de una tienda. Accesible para el ADMIN de esa
     * misma tienda o para SUPER_ADMIN.
     *
     * @param id    identificador de la tienda
     * @param file  archivo de imagen del logo (multipart/form-data)
     * @param actor usuario autenticado que realiza la carga
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @PostMapping(value = "/{id}/logo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Tienda>> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaLogoService.upload(id, file, actor), "Logo actualizado"));
    }

    /**
     * Elimina el logo personalizado de una tienda; a partir de este cambio se usará el
     * logo por default. Accesible para el ADMIN de esa misma tienda o para SUPER_ADMIN/
     * SUPERVISOR sobre las suyas.
     *
     * @param id    identificador de la tienda
     * @param actor usuario autenticado que realiza la eliminación
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    @DeleteMapping("/{id}/logo")
    public ResponseEntity<ApiResponse<Tienda>> removeLogo(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaLogoService.remove(id, actor), "Logo eliminado, se usará el logo por default"));
    }
}
