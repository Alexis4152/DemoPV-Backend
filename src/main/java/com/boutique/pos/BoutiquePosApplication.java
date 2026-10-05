package com.boutique.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BoutiquePosApplication {

    // Fija la zona horaria del JVM antes de que arranque el contexto de Spring, para que
    // LocalDateTime.now() (fecha de ventas, cortes de caja, movimientos de inventario,
    // apartados, auditoría createdAt/updatedAt, etc. — todo el sistema usa LocalDateTime,
    // nunca Instant/ZonedDateTime) refleje la hora de México sin importar en qué zona
    // horaria corra el servidor (Hostinger arranca los contenedores en UTC por default,
    // de ahí las 6 horas de diferencia que veía el cliente).
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BoutiquePosApplication.class, args);
    }
}
