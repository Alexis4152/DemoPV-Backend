package com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenpayChargeRequest(
        String method,
        @JsonProperty("source_id") String sourceId,
        BigDecimal amount,
        String currency,
        String description,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("device_session_id") String deviceSessionId,
        OpenpayCustomerDto customer,
        Boolean confirm
) {}
