package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.ArcoRequestExecute;
import com.boutique.pos.dto.ArcoRequestExtend;
import com.boutique.pos.dto.ArcoRequestInPersonCreate;
import com.boutique.pos.dto.ArcoRequestInfoRequest;
import com.boutique.pos.dto.ArcoRequestRespond;
import com.boutique.pos.dto.ArcoRequestVerifyIdentity;
import com.boutique.pos.dto.ArcoRequestView;
import com.boutique.pos.model.ArcoRequestStatus;
import com.boutique.pos.model.User;
import com.boutique.pos.service.ArcoRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administra todas las solicitudes ARCO, expuesto bajo {@code /api/admin/arco-requests}.
 * Exclusivo de SUPER_ADMIN — igual que la configuración de correo ({@code
 * AdminMailConfigController}), es infraestructura/obligación legal de la plataforma
 * completa, no algo por tienda que un ADMIN o SUPERVISOR puedan atender.
 */
@RestController
@RequestMapping("/api/admin/arco-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminArcoRequestController {

    private final ArcoRequestService arcoRequestService;

    @GetMapping
    public ApiResponse<List<ArcoRequestView>> list(@RequestParam(required = false) ArcoRequestStatus status) {
        return ApiResponse.ok(arcoRequestService.list(status), null);
    }

    @GetMapping("/pending-count")
    public ApiResponse<Long> pendingCount() {
        return ApiResponse.ok(arcoRequestService.countPending(), null);
    }

    @GetMapping("/{id}")
    public ApiResponse<ArcoRequestView> get(@PathVariable Long id) {
        return ApiResponse.ok(arcoRequestService.getById(id), null);
    }

    /** Registra una solicitud en mostrador, para alguien verificado en persona. */
    @PostMapping("/in-person")
    public ApiResponse<ArcoRequestView> createInPerson(
            @Valid @RequestBody ArcoRequestInPersonCreate request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.createInPerson(request, actor), "Solicitud registrada");
    }

    @PostMapping("/{id}/verify-identity")
    public ApiResponse<ArcoRequestView> verifyIdentity(
            @PathVariable Long id, @Valid @RequestBody ArcoRequestVerifyIdentity request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.verifyIdentity(id, request, actor), "Identidad verificada");
    }

    @PostMapping("/{id}/request-info")
    public ApiResponse<ArcoRequestView> requestInfo(
            @PathVariable Long id, @Valid @RequestBody ArcoRequestInfoRequest request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.requestInfo(id, request, actor), "Se le pidió información al titular");
    }

    @PostMapping("/{id}/extend")
    public ApiResponse<ArcoRequestView> extend(
            @PathVariable Long id, @Valid @RequestBody ArcoRequestExtend request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.extend(id, request, actor), "Plazo extendido");
    }

    @PostMapping("/{id}/respond")
    public ApiResponse<ArcoRequestView> respond(
            @PathVariable Long id, @Valid @RequestBody ArcoRequestRespond request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.respond(id, request, actor), "Solicitud respondida");
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<ArcoRequestView> execute(
            @PathVariable Long id, @Valid @RequestBody ArcoRequestExecute request, @AuthenticationPrincipal User actor) {
        return ApiResponse.ok(arcoRequestService.execute(id, request, actor), "Solicitud ejecutada y completada");
    }

    @GetMapping(value = "/{id}/constancia.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> constancia(@PathVariable Long id) {
        byte[] pdf = arcoRequestService.constanciaPdf(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"constancia-arco-" + id + ".pdf\"")
                .body(pdf);
    }
}
