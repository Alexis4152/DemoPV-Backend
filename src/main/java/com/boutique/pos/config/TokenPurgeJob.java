package com.boutique.pos.config;

import com.boutique.pos.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job programado que purga los refresh tokens y tokens de recuperación de contraseña ya
 * vencidos — ver {@link AuthService#purgeExpiredTokens()}. Sin esto, {@code refresh_tokens}
 * y {@code password_reset_tokens} crecen sin límite con el tiempo (hallazgo "Baja" de la
 * auditoría de código), ya que ninguna pantalla los lee ni los necesita una vez vencidos.
 * Mismo patrón que {@code ApartadoExpiryJob}/{@code CashCutAutoCloseJob}, pero corre una
 * vez por hora: a diferencia de esos, aquí no hay ninguna urgencia de negocio, solo limpieza.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenPurgeJob {

    private final AuthService authService;

    @Scheduled(fixedRate = 60 * 60_000)
    public void purge() {
        int deleted = authService.purgeExpiredTokens();
        if (deleted > 0) {
            log.info("Purga de tokens vencidos: {} fila(s) borrada(s) (refresh_tokens + password_reset_tokens)", deleted);
        }
    }
}
