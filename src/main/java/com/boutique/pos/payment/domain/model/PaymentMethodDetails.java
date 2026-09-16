package com.boutique.pos.payment.domain.model;

public record PaymentMethodDetails(
        String reference,
        String barcodeUrl,
        String clabe,
        String bank,
        String redirectUrl
) {
    public static PaymentMethodDetails forStore(String reference, String barcodeUrl) {
        return new PaymentMethodDetails(reference, barcodeUrl, null, null, null);
    }

    public static PaymentMethodDetails forSpei(String clabe, String bank) {
        return new PaymentMethodDetails(null, null, clabe, bank, null);
    }

    public static PaymentMethodDetails for3DSecure(String redirectUrl) {
        return new PaymentMethodDetails(null, null, null, null, redirectUrl);
    }

    public static PaymentMethodDetails empty() {
        return new PaymentMethodDetails(null, null, null, null, null);
    }
}
