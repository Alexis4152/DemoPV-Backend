package com.boutique.pos.payment.application.port.in;

public record WebhookNotification(
        String type,
        String eventDate,
        String openpayTransactionId,
        String status,
        String authorizationCode,
        String errorMessage
) {}
