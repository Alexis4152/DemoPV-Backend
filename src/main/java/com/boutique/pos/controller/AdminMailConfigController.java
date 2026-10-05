package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.MailConfigRequest;
import com.boutique.pos.dto.MailConfigResponse;
import com.boutique.pos.model.User;
import com.boutique.pos.service.MailConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administra la configuración SMTP global (una sola cuenta con la que sale TODO correo del
 * sistema), expuesta bajo {@code /api/admin/mail-config}. Exclusivo de SUPER_ADMIN — es
 * infraestructura de la plataforma, no algo por tienda; el correo que un ADMIN/SUPERVISOR de
 * tienda sí puede editar es el de "Responder a" en {@code TiendaInfoController} (Datos de la
 * tienda), no las credenciales SMTP.
 */
@RestController
@RequestMapping("/api/admin/mail-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMailConfigController {

    private final MailConfigService mailConfigService;

    @GetMapping
    public ApiResponse<MailConfigResponse> get() {
        return ApiResponse.ok(mailConfigService.get());
    }

    @PutMapping
    public ApiResponse<MailConfigResponse> update(@Valid @RequestBody MailConfigRequest request,
                                                   @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(mailConfigService.update(request, actor), "Configuración de correo actualizada");
    }
}
