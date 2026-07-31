package com.boutique.pos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync: usado por EmailService para no bloquear la venta mientras se manda el ticket.
// @EnableScheduling: usado por CashCutAutoCloseJob (cierre automático de cortes de caja).
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
