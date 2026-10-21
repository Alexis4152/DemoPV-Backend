package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ArcoRequestCreate;
import com.boutique.pos.service.ArcoRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Formulario público de solicitudes ARCO (Acceso, Rectificación, Cancelación, Oposición),
 * expuesto bajo {@code /api/public/arco-requests} — SIN autenticación (ver {@code
 * SecurityConfig}, permite todo bajo {@code /api/public/**}), igual que la vitrina de
 * apartados. A propósito NO cuelga de {@code /api/public/tiendas/{slug}/**} como esa
 * vitrina: una solicitud ARCO no es de una tienda, es de la plataforma completa — la
 * atiende SUPER_ADMIN (ver {@code AdminArcoRequestController}), nunca el admin de una
 * tienda. Limitado en tasa por IP (ver {@code RateLimitInterceptor}) para que nadie lo use
 * para inundar de correos a los SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/public/arco-requests")
@RequiredArgsConstructor
public class PublicArcoController {

    private final ArcoRequestService arcoRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> create(@Valid @RequestBody ArcoRequestCreate request) {
        arcoRequestService.create(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Tu solicitud fue enviada. Te contactaremos a tu correo."));
    }
}
