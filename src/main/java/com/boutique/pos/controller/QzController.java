package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.service.QzSigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Expone el certificado público y la firma que el frontend necesita para autenticar sus
 * conexiones a QZ Tray al imprimir tickets (ver {@code utils/printer.js} del frontend y
 * {@link QzSigningService} para el porqué de todo esto).
 * <p>
 * No requiere ninguna regla de autorización especial más allá de estar logueado (igual
 * que el resto de la API): el certificado es público por naturaleza, y firmar solo
 * produce la firma de un texto que QZ Tray genera y ya conoce del otro lado — no hay
 * nada sensible que proteger aquí distinto del resto de la API.
 */
@RestController
@RequestMapping("/api/qz")
@RequiredArgsConstructor
@Slf4j
public class QzController {

    private final QzSigningService qzSigningService;

    /** Certificado público en PEM, para {@code qz.security.setCertificatePromise}. */
    @GetMapping("/certificate")
    public ResponseEntity<ApiResponse<String>> certificate() {
        log.info("GET /api/qz/certificate");
        return ResponseEntity.ok(ApiResponse.ok(qzSigningService.getCertificate()));
    }

    /** Firma el texto que QZ Tray manda a {@code qz.security.setSignaturePromise}. */
    @PostMapping("/sign")
    public ResponseEntity<ApiResponse<String>> sign(@RequestBody Map<String, String> body) {
        log.info("POST /api/qz/sign");
        return ResponseEntity.ok(ApiResponse.ok(qzSigningService.sign(body.get("data"))));
    }
}
