package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.TiendaInfoRequest;
import com.boutique.pos.dto.TiendaRequest;
import com.boutique.pos.dto.TiendaThemeRequest;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.TiendaInfo;
import com.boutique.pos.model.User;
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

@RestController
@RequestMapping("/api/tiendas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TiendaController {

    private final TiendaService tiendaService;
    private final TiendaInfoService tiendaInfoService;
    private final TiendaLogoService tiendaLogoService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Tienda>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findAll(), null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.findById(id), null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Tienda>> create(@Valid @RequestBody TiendaRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.create(req), "Tienda creada"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Tienda>> update(@PathVariable Long id, @Valid @RequestBody TiendaRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.update(id, req), "Tienda actualizada"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        tiendaService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tienda desactivada"));
    }

    // El ADMIN de una tienda también puede cambiar SU propio color de marca —
    // este método sobreescribe el @PreAuthorize de la clase (solo SUPER_ADMIN).
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/theme")
    public ResponseEntity<ApiResponse<Tienda>> updateTheme(
            @PathVariable Long id,
            @Valid @RequestBody TiendaThemeRequest req,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaService.updateTheme(id, req.getPrimaryColor(), actor), "Color actualizado"));
    }

    // Datos fiscales/de contacto y logo — igual que /theme, el ADMIN de esa tienda
    // (o SUPER_ADMIN) puede leer y editar, nunca el ADMIN de otra tienda.
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/info")
    public ResponseEntity<ApiResponse<TiendaInfo>> getInfo(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.get(id, actor), null));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/{id}/info")
    public ResponseEntity<ApiResponse<TiendaInfo>> updateInfo(
            @PathVariable Long id,
            @Valid @RequestBody TiendaInfoRequest req,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaInfoService.update(id, req, actor), "Datos de la tienda actualizados"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping(value = "/{id}/logo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<Tienda>> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User actor
    ) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaLogoService.upload(id, file, actor), "Logo actualizado"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/{id}/logo")
    public ResponseEntity<ApiResponse<Tienda>> removeLogo(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(tiendaLogoService.remove(id, actor), "Logo eliminado, se usará el logo por default"));
    }
}
