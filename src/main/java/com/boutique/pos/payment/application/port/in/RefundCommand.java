package com.boutique.pos.payment.application.port.in;

import java.math.BigDecimal;

public record RefundCommand(
        BigDecimal amount,
        String reason
) {}
