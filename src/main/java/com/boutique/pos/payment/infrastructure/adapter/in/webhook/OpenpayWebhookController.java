package com.boutique.pos.payment.infrastructure.adapter.in.webhook;

import com.boutique.pos.payment.application.port.in.ProcessWebhookUseCase;
import com.boutique.pos.payment.application.port.in.WebhookNotification;
import com.boutique.pos.payment.infrastructure.config.OpenpayProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/webhooks/openpay")
@Tag(name = "Webhooks Openpay", description = "Recepción y procesamiento de eventos asíncronos de la pasarela")
public class OpenpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(OpenpayWebhookController.class);

    private final ProcessWebhookUseCase processWebhookUseCase;
    private final OpenpayProperties properties;

    public OpenpayWebhookController(ProcessWebhookUseCase processWebhookUseCase, OpenpayProperties properties) {
        this.processWebhookUseCase = processWebhookUseCase;
        this.properties = properties;
    }

    @PostMapping
    @Operation(summary = "Recepción de Webhooks de Openpay", description = "Procesa notificaciones de pagos exitosos, fallidos o reembolsos de forma idempotente.")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody OpenpayWebhookPayload payload
    ) {
        log.info("Notificación webhook recibida: {}", payload != null ? payload.type() : "null");

        // Verificación de autenticación básica configurada para el webhook
        if (properties.getWebhookUser() != null && !properties.getWebhookUser().isBlank()) {
            if (!isAuthorized(authHeader)) {
                log.warn("Petición de webhook rechazada por credenciales inválidas");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        if (payload == null || payload.transaction() == null || payload.transaction().id() == null) {
            log.warn("Payload de webhook no contiene datos de transacción, omitiendo");
            return ResponseEntity.ok().build();
        }

        var notification = new WebhookNotification(
                payload.type(),
                payload.eventDate(),
                payload.transaction().id(),
                payload.transaction().status(),
                payload.transaction().authorization(),
                payload.transaction().errorMessage()
        );

        processWebhookUseCase.execute(notification);

        // Responder siempre 200 OK a Openpay para confirmar la recepción
        return ResponseEntity.ok().build();
    }

    private boolean isAuthorized(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            String[] values = credentials.split(":", 2);

            return values.length == 2 &&
                    properties.getWebhookUser().equals(values[0]) &&
                    properties.getWebhookPassword().equals(values[1]);
        } catch (Exception e) {
            log.error("Error evaluando cabecera de autorización de webhook", e);
            return false;
        }
    }
}
