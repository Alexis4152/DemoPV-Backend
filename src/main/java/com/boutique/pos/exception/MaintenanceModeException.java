package com.boutique.pos.exception;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Hay una ventana de mantenimiento activa y el usuario no es SUPER_ADMIN: no puede iniciar
 * sesión ni operar hasta que termine. Se responde con 503 y {@code data.maintenance = true}
 * para que el frontend muestre la pantalla de mantenimiento (ver GlobalExceptionHandler).
 */
@Getter
public class MaintenanceModeException extends RuntimeException {

    private final LocalDateTime endAt;

    public MaintenanceModeException(LocalDateTime endAt) {
        super("El sistema está en mantenimiento. Intenta de nuevo cuando termine.");
        this.endAt = endAt;
    }
}
