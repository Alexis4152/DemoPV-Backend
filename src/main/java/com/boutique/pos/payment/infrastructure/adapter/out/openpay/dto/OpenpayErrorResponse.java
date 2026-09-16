package com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenpayErrorResponse(
        String category,
        String description,
        @JsonProperty("http_code") Integer httpCode,
        @JsonProperty("error_code") Integer errorCode,
        @JsonProperty("request_id") String requestId
) {}
