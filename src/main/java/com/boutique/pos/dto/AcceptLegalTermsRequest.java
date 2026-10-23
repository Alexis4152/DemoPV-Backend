package com.boutique.pos.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * Payload de {@code POST /api/auth/accept-legal-terms} — ambas casillas son obligatorias
 * (igual que las exige el frontend en la pantalla de aceptación), así que si cualquiera
 * llega en {@code false} la petición se rechaza con 400 antes de tocar el servicio.
 */
@Data
public class AcceptLegalTermsRequest {
    @AssertTrue(message = "Debes aceptar los Términos y condiciones")
    private boolean acceptTerms;

    @AssertTrue(message = "Debes confirmar que leíste el Aviso de privacidad")
    private boolean acceptPrivacyNotice;
}
