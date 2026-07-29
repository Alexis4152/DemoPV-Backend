package com.boutique.pos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// habilita @Async (usado por EmailService para no bloquear la venta mientras se manda el ticket)
@Configuration
@EnableAsync
public class AsyncConfig {
}
