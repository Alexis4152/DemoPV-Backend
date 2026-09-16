package com.boutique.pos.payment.infrastructure.adapter.in.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenpayWebhookPayload(
        String type,
        @JsonProperty("event_date") String eventDate,
        WebhookTransaction transaction
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookTransaction(
            String id,
            String authorization,
            String status,
            @JsonProperty("error_message") String errorMessage
    ) {}
}
