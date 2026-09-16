package com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenpayChargeResponse(
        String id,
        String authorization,
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("transaction_type") String transactionType,
        String status,
        BigDecimal amount,
        String currency,
        @JsonProperty("creation_date") String creationDate,
        @JsonProperty("operation_date") String operationDate,
        String description,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("payment_method") OpenpayPaymentMethodDto paymentMethod
) {}
