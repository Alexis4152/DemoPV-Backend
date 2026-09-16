package com.boutique.pos.payment.domain.model;

public record CustomerInfo(
        String name,
        String lastName,
        String email,
        String phoneNumber
) {
}
