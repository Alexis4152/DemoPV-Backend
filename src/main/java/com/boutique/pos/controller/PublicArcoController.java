package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ArcoRequestCreate;
import com.boutique.pos.dto.ArcoRequestInfoAnswer;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.service.ArcoRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Formulario público de solicitudes ARCO y su portal de seguimiento por link, expuestos
 * bajo {@code /api/public/arco-requests} — SIN autenticación (ver {@code SecurityConfig},
 * permite todo bajo {@code /api/public/**}), igual que la vitrina de apartados. A propósito
 * NO cuelga de {@code /api/public/tiendas/{slug}/**} como esa vitrina: una solicitud ARCO no
 * es de una tienda, es de la plataforma completa — la atiende SUPER_ADMIN (ver {@code
 * AdminArcoRequestController}), nunca el admin de una tienda. Limitado en tasa por IP (ver
 * {@code RateLimitInterceptor}) para que nadie lo use para inundar de correos a los
 * SUPER_ADMIN ni para probar tokens de seguimiento al azar.
 */
@RestController
@RequestMapping("/api/public/arco-requests")
@RequiredArgsConstructor
public class PublicArcoController {

    private final ArcoRequestService arcoRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> create(@Valid @RequestBody ArcoRequestCreate request) {
        arcoRequestService.createPublic(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Revisa tu correo para confirmar tu solicitud."));
    }

    /** Portal de seguimiento: visitar el link confirma el correo (si hacía falta) y muestra el estado actual. */
    @GetMapping("/track/{token}")
    public ResponseEntity<ApiResponse<ArcoRequestView>> track(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(arcoRequestService.trackByToken(token), null));
    }

    /** El titular responde, desde el link de seguimiento, lo que el personal le pidió. */
    @PostMapping("/track/{token}/answer-info")
    public ResponseEntity<ApiResponse<ArcoRequestView>> answerInfo(
            @PathVariable String token, @Valid @RequestBody ArcoRequestInfoAnswer request) {
        return ResponseEntity.ok(ApiResponse.ok(arcoRequestService.answerInfoByToken(token, request), "Tu respuesta fue enviada."));
    }
}
