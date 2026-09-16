package com.boutique.pos.payment.application.port.in;

public interface ProcessWebhookUseCase {
    void execute(WebhookNotification notification);
}
