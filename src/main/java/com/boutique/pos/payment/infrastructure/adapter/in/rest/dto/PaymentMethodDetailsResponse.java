package com.boutique.pos.payment.infrastructure.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detalles específicos del método de pago (referencias para tienda, spei o 3D secure)")
public record PaymentMethodDetailsResponse(
        @Schema(description = "Referencia numérica para pago en tiendas de conveniencia (Paynet)", example = "101010293812")
        String reference,

        @Schema(description = "URL para visualizar o imprimir el código de barras", example = "https://sandbox-api.openpay.mx/barcode/101010293812")
        String barcodeUrl,

        @Schema(description = "Cuenta CLABE interbancaria única para transferencia SPEI", example = "646180111800000000")
        String clabe,

        @Schema(description = "Banco receptor para la transferencia", example = "STP")
        String bank,

        @Schema(description = "URL de redirección para autenticación 3D Secure", example = "https://sandbox-api.openpay.mx/pay/3ds/...")
        String redirectUrl
) {}
