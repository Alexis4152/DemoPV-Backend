package com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenpayCustomerDto(
        String name,
        @JsonProperty("last_name") String lastName,
        String email,
        @JsonProperty("phone_number") String phoneNumber
) {}
