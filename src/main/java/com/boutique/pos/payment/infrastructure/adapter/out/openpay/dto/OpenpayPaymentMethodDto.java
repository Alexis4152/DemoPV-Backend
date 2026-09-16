package com.boutique.pos.payment.infrastructure.adapter.out.openpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenpayPaymentMethodDto(
        String type,
        String reference,
        @JsonProperty("barcode_url") String barcodeUrl,
        String clabe,
        String bank,
        String url
) {}
