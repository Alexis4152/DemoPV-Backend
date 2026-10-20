package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ArcoRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "Mis solicitudes ARCO", expuesto bajo {@code /api/arco-requests} — requiere sesión
 * (cualquier rol: CASHIER, ADMIN, SUPERVISOR, SUPER_ADMIN), sin exigir ninguna {@code
 * AppSection} en particular, porque no es una función de la tienda sino del usuario mismo
 * viendo su propio historial. A diferencia de {@code AdminArcoRequestController} (todas las
 * solicitudes, exclusivo de SUPER_ADMIN) y {@code PublicArcoController} (levantar una
 * nueva, sin sesión), este solo deja ver — nunca atender — y solo las que levantó el
 * propio usuario en sesión.
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
}
