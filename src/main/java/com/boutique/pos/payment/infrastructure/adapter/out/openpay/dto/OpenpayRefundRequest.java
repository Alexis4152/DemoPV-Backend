package com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenpayRefundRequest(
        String description,
        BigDecimal amount
) {}
