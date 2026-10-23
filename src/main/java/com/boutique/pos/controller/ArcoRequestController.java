package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ArcoRequestCreate;
import com.boutique.pos.dto.ArcoRequestInfoAnswer;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ArcoRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * "Mis solicitudes ARCO", expuesto bajo {@code /api/arco-requests} — requiere sesión
 * (cualquier rol: CASHIER, ADMIN, SUPERVISOR, SUPER_ADMIN), sin exigir ninguna {@code
 * AppSection} en particular, porque no es una función de la tienda sino del usuario mismo
 * viendo/levantando sus propias solicitudes. A diferencia de {@code
 * AdminArcoRequestController} (todas las solicitudes, exclusivo de SUPER_ADMIN) y {@code
 * PublicArcoController} (levantar una nueva sin sesión), aquí cada quien solo ve y levanta
 * las SUYAS.
 */
@RestController
@RequestMapping("/api/arco-requests")
@RequiredArgsConstructor
public class ArcoRequestController {

    private final ArcoRequestService arcoRequestService;

    @GetMapping("/mine")
    public ApiResponse<List<ArcoRequestView>> mine(@AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.listMine(actor), null);
    }

    /** Levanta una solicitud a nombre de la propia cuenta en sesión (canal ACCOUNT). */
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody ArcoRequestCreate request, @AuthenticationPrincipal User actor) {
        arcoRequestService.createOwn(request, actor);
        return ApiResponse.ok(null, "Tu solicitud fue enviada.");
    }

    /** Responde, desde la propia cuenta, lo que el personal pidió sobre una solicitud propia. */
    @PostMapping("/{id}/answer-info")
    public ApiResponse<ArcoRequestView> answerInfo(
            @PathVariable Long id, @Valid @RequestBody ArcoRequestInfoAnswer request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.answerInfoMine(id, request, actor), "Tu respuesta fue enviada.");
    }
}
